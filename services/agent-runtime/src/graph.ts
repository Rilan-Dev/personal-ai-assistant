import { Annotation, StateGraph, START, END } from "@langchain/langgraph";
import type { PlannerDecision } from "../../../packages/agent-contracts/src/index.ts";
import {
  PlanningRequestSchema,
  enforceDecision,
  redactObservations,
  selectCatalog,
  type PlanningRequest
} from "./policy.ts";

/**
 * LangGraph is the execution harness for model orchestration; native device
 * actions are NEVER executed in this graph. One graph.invoke = one planning
 * step. The phone's AgentHarness owns the observe/execute/verify outer loop.
 */
const PlanningState = Annotation.Root({
  request: Annotation<PlanningRequest>,
  proposed: Annotation<unknown>(),
  decision: Annotation<PlannerDecision>()
});

export type PlanContext = {
  request: string;
  previousActions: PlanningRequest["observations"];
  tools: ReturnType<typeof selectCatalog>;
  /** Untrusted tool outputs are observations, not new instructions. */
};
export interface PlanningModel {
  decide(context: PlanContext): Promise<unknown>;
}

export class LangGraphStepPlanner {
  private readonly graph;

  constructor(model: PlanningModel) {
    this.graph = new StateGraph(PlanningState)
      .addNode("model", async state => {
        const context = redactObservations(state.request);
        const proposed = await model.decide({
          request: context.request,
          previousActions: context.observations,
          tools: selectCatalog(context.availableToolIds)
        });
        return { proposed };
      })
      .addNode("policy", state => ({
        decision: enforceDecision(state.proposed, state.request)
      }))
      .addEdge(START, "model")
      .addEdge("model", "policy")
      .addEdge("policy", END)
      .compile();
  }

  async next(raw: unknown, signal?: AbortSignal): Promise<PlannerDecision> {
    const input = PlanningRequestSchema.parse(raw);
    const result = await this.graph.invoke({ request: input }, {
      recursionLimit: 6,
      signal
    });
    return result.decision;
  }
}
