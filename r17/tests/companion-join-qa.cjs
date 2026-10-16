// Isolated loopback admission tests. This script cannot target an external server.
const mineflayer=require('mineflayer'),fs=require('node:fs'),path=require('node:path'),{execFileSync}=require('node:child_process');
const {writeAction}=require('./dialog-qa-wire.cjs');
const mode=process.argv[2]||'missing';
if(!['missing','meteor','clean','incomplete','stale','malformed','old','exit','clean-remote'].includes(mode))throw Error('Unknown QA mode');
const root=path.resolve(__dirname,'..'),jar=path.join(root,'build/AegisAC-R17-Companion-preview2-hotfix1-1.21.11.jar');
const result={mode,started:new Date().toISOString(),spawned:false,dialogs:[],challenge:false,authMessages:[],errors:[]};
const usernames={missing:'NoCompanionQA',meteor:'MeteorBlockedQA',clean:'AegisCleanQA',incomplete:'IncompleteQA',stale:'StaleReportQA',malformed:'MalformedQA',old:'OldCompanionQA',exit:'ExitDialogQA','clean-remote':'AegisCleanQA'};
let finished=false;
const bot=mineflayer.createBot({host:'127.0.0.1',port:25581,version:'1.21.11',auth:'offline',username:usernames[mode],hideErrors:true});
bot._client.on('state',state=>{(result.states??=[]).push({state,at:new Date().toISOString()});if(state==='configuration'&&mode!=='missing')setImmediate(()=>bot._client.write('custom_payload',{channel:'minecraft:register',data:Buffer.from('aegisac:challenge_v1')}));});
const wait=ms=>new Promise(r=>setTimeout(r,ms));
function save(code){if(finished)return;finished=true;result.finished=new Date().toISOString();fs.mkdirSync(path.join(root,'results/companion'),{recursive:true});fs.writeFileSync(path.join(root,`results/companion/${mode}.json`),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));bot.end();setTimeout(()=>process.exit(code),200);}
bot.on('physicsTick',()=>setImmediate(()=>{if(result.spawned&&!finished)bot._client.write('tick_end',{});}));
bot._client.on('packet',(data,meta)=>{
  if(meta.name==='show_dialog'){
    result.dialogs.push(data);
    const text=JSON.stringify(data);
    if(mode==='exit'&&text.includes('aegisac:leave_verification'))writeAction(bot._client,'aegisac:leave_verification');
    if(['meteor','missing','incomplete','stale','old'].includes(mode)&&/Prohibited client reported|Companion required|Companion update required|Verification incomplete/.test(text))writeAction(bot._client,'aegisac:leave_verification');
    if(['clean','clean-remote'].includes(mode)){
      const action=text.includes('authme:prejoin-register/submit')?'authme:prejoin-register/submit':text.includes('authme:prejoin-login/submit')?'authme:prejoin-login/submit':null;
      if(action){result.authDialogSubmitted=action;writeAction(bot._client,action,{type:'compound',name:'',value:{password:{type:'string',value:'R17qaOnly8236!'},confirm:{type:'string',value:'R17qaOnly8236!'}}});}
    }
  }
  if(meta.name!=='custom_payload'||data.channel!=='aegisac:challenge_v1')return;
  result.challenge=true;result.challengeAt=new Date().toISOString();const bytes=Buffer.from(data.data);if(bytes.length!==44||bytes.readUInt32BE(0)!==0x41474331){result.errors.push('Invalid challenge');save(1);return;}
  if(['missing','exit'].includes(mode))return;
  try{
    const input=mode==='meteor'?path.join(root,'inputs/meteor-client-1.21.11-86.jar'):mode==='incomplete'?path.join(root,'inputs/intentionally-missing.jar'):path.join(root,'inputs/sodium-1.21.11.jar');
    const scan=JSON.parse(execFileSync('java',['-cp',jar,path.join(__dirname,'CompanionScanCli.java'),input,bytes.subarray(8,40).toString('base64')],{encoding:'utf8',timeout:10000,windowsHide:true}));
    let report=Buffer.from(scan.report,'base64');delete scan.report;result.scan=scan;
    if(mode==='stale')report[8]^=1;
    if(mode==='old'){const buildLength=report.readUInt16BE(40);report.fill(0x78,42,42+buildLength);}
    if(mode==='malformed')report=Buffer.from([1,2,3]);
    bot._client.write('custom_payload',{channel:'aegisac:report_v1',data:report});result.reportSentAt=new Date().toISOString();
  }catch(error){result.errors.push(error.message);save(1);}
});
bot.on('messagestr',message=>{if(/register|log in|login|logged in|success|authent/i.test(message))result.authMessages.push(message.replaceAll('R17qaOnly8236!','<local-test-password>'));});
bot.on('error',error=>{result.errors.push(error.message);save(1);});
bot.on('kicked',reason=>{
  result.kicked=reason;const text=JSON.stringify(reason),dialogs=JSON.stringify(result.dialogs);
  const okay=mode==='clean-remote'?result.spawned&&text.includes('Remote Interaction Blocked'):!result.spawned&&text.includes('AegisAC')&&(mode!=='meteor'||(text.includes('Meteor Client')&&dialogs.includes('Prohibited client reported')))&&(mode!=='missing'||dialogs.includes('Companion required'));
  save(okay&&result.errors.length===0?0:1);
});
bot.once('spawn',async()=>{
  result.spawned=true;if(!['clean','clean-remote'].includes(mode)){result.errors.push('Unverified client entered the world');save(1);return;}
  try{
    await wait(1000);bot.chat('/register R17qaOnly8236! R17qaOnly8236!');await wait(2500);bot.chat('/login R17qaOnly8236!');await wait(2500);
    result.authPromptObserved=result.authMessages.some(s=>!/Unknown or incomplete|HERE/i.test(s));result.authenticated=result.authMessages.some(s=>/successfully|successful|logged in/i.test(s));
    if(!result.authenticated){result.errors.push('AuthMe authentication was not observed');save(1);return;}
    if(mode==='clean'){save(result.authPromptObserved?0:1);return;}
    await wait(7000);const base=bot.entity.position.floored();
    for(let i=0;i<5&&!finished;i++){bot._client.write('block_dig',{status:0,location:{x:base.x+20,y:base.y-1,z:base.z},face:1,sequence:i+1});await wait(500);}
    await wait(2500);if(!finished){result.errors.push('Expected remote guard kick missing');save(1);}
  }catch(error){result.errors.push(error.message);save(1);}
});
setTimeout(()=>{if(!finished){result.errors.push('Admission QA timed out');save(1);}},55000);
