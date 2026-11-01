import {NativeModules,PermissionsAndroid,Platform} from 'react-native';

export type OwnerApproximateLocation={
  latitude:number;longitude:number;
  precision:'two-decimal-degrees';
  source:'android-location-manager';
};
export type OwnerSelectedContact={
  name:string;phone:string;source:'android-contact-picker';
};

interface NativeOwnerPrivacy{
  readApproximateLocation():Promise<OwnerApproximateLocation>;
  pickPhoneContact():Promise<OwnerSelectedContact|null>;
}
function native():NativeOwnerPrivacy{
  const value=NativeModules.PAAOwnerPrivacy;
  if(Platform.OS!=='android'||!value?.readApproximateLocation||!value?.pickPhoneContact)
    throw new Error('OWNER_PRIVACY_UNAVAILABLE');
  return value as NativeOwnerPrivacy;
}
export const ownerPrivacyAvailable=()=>Platform.OS==='android' &&
  typeof NativeModules.PAAOwnerPrivacy?.pickPhoneContact==='function';

export async function getOwnerApproximateLocation():Promise<OwnerApproximateLocation|null>{
  if(!ownerPrivacyAvailable())throw new Error('OWNER_PRIVACY_UNAVAILABLE');
  const permission=PermissionsAndroid.PERMISSIONS.ACCESS_COARSE_LOCATION;
  const granted=await PermissionsAndroid.check(permission) ||
    (await PermissionsAndroid.request(permission,{
      title:'Share approximate location once',
      message:'Android can provide an approximate position when you request it. It is rounded to two decimal places, shown for review, and never read continuously or uploaded automatically.',
      buttonPositive:'Allow one-time read',
      buttonNegative:'Cancel'
    }))===PermissionsAndroid.RESULTS.GRANTED;
  if(!granted)return null;
  const result=await native().readApproximateLocation();
  if(result?.source!=='android-location-manager' ||
    result.precision!=='two-decimal-degrees' ||
    !Number.isFinite(result.latitude)||!Number.isFinite(result.longitude)||
    Math.abs(result.latitude)>90||Math.abs(result.longitude)>180)
    throw new Error('INVALID_LOCATION_DATA');
  return result;
}

export async function pickOwnerContact():Promise<OwnerSelectedContact|null>{
  const result=await native().pickPhoneContact();
  if(result===null)return null;
  if(typeof result?.name!=='string'||typeof result.phone!=='string'||
    !result.phone.trim()||result.name.length>160||result.phone.length>80||
    result.source!=='android-contact-picker')
    throw new Error('INVALID_CONTACT_DATA');
  return result;
}
