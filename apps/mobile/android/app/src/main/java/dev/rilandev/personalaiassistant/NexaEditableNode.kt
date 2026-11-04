package dev.rilandev.personalaiassistant

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Android may expose an EMPTY EditText's placeholder as node.text. Treat
 * isShowingHintText as empty while refusing to overwrite actual user content.
 * This reads only the target field locally; no text is logged or sent to AI.
 */
internal object NexaEditableNode {
    fun isEmpty(node: AccessibilityNodeInfo): Boolean =
        node.isEditable && (node.text.isNullOrEmpty() ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && node.isShowingHintText))

    /** Read-only post-action refresh. Never triggers a second text insertion. */
    fun containsExactTextAfterDispatch(node:AccessibilityNodeInfo,expected:String):Boolean {
        return try {
            node.refresh() &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !node.isShowingHintText) &&
                node.text?.toString()==expected
        } catch (_:Exception) { false }
    }
}
