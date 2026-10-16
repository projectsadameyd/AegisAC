// minecraft-data's click-action schema omits the length-prefixed optional NBT used by 1.21.11.
// Keep this correction local to the protocol QA harness; never change the installed dependency.
const nbt=require('prismarine-nbt');
const varint=n=>{const a=[];do{let b=n&127;n>>>=7;if(n)b|=128;a.push(b);}while(n);return Buffer.from(a);};
exports.writeAction=(client,id,tag)=>{
  if(client.state!=='configuration')throw Error('QA dialog action requires configuration state');
  const name=Buffer.from(id,'utf8');let payload=Buffer.from([0]);
  if(tag){const named=nbt.writeUncompressed({...tag,name:''});payload=Buffer.concat([named.subarray(0,1),named.subarray(3)]);}
  client.writeRaw(Buffer.concat([varint(0x08),varint(name.length),name,varint(payload.length),payload]));
};
