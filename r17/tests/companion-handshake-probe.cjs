// Reproduces the configuration-channel race against the isolated loopback Paper/AuthMe server.
const mineflayer=require('mineflayer'),path=require('node:path'),fs=require('node:fs'),{execFileSync}=require('node:child_process');
const {writeAction}=require('./dialog-qa-wire.cjs');
const delay=Number(process.argv[2]??1000),flavor=process.argv[3]??'preview2';
if(!Number.isInteger(delay)||delay<0||delay>5000||!['preview2','preview2-hotfix1'].includes(flavor))throw Error('Invalid local QA argument');
const root=path.resolve(__dirname,'..'),result={delay,flavor,challenge:false,registered:false,spawned:false};
const jar=path.join(root,`build/AegisAC-R17-Companion-${flavor}-1.21.11.jar`);
const bot=mineflayer.createBot({host:'127.0.0.1',port:25581,version:'1.21.11',auth:'offline',username:'AegisCleanQA',hideErrors:true});
let done=false;
function finish(code){if(done)return;done=true;fs.mkdirSync(path.join(root,'results/companion'),{recursive:true});fs.writeFileSync(path.join(root,`results/companion/handshake-${flavor}-${delay}ms.json`),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));bot.end();setTimeout(()=>process.exit(code),100);}
bot._client.on('state',state=>{if(state==='configuration')setTimeout(()=>{if(!done){bot._client.write('custom_payload',{channel:'minecraft:register',data:Buffer.from('aegisac:challenge_v1')});result.registered=true;}},delay);});
bot._client.on('packet',(data,meta)=>{
  if(meta.name==='show_dialog'){
    const text=JSON.stringify(data);
    if(text.includes('Companion required')){result.companionRequired=true;writeAction(bot._client,'aegisac:leave_verification');}
    const action=text.includes('authme:prejoin-register/submit')?'authme:prejoin-register/submit':text.includes('authme:prejoin-login/submit')?'authme:prejoin-login/submit':null;
    if(action){result.authDialog=action;writeAction(bot._client,action,{type:'compound',name:'',value:{password:{type:'string',value:'R17qaOnly8236!'},confirm:{type:'string',value:'R17qaOnly8236!'}}});}
  }
  if(meta.name==='custom_payload'&&data.channel==='aegisac:challenge_v1'){
    result.challenge=true;
    try{const bytes=Buffer.from(data.data),scan=JSON.parse(execFileSync('java',['-cp',jar,path.join(__dirname,'CompanionScanCli.java'),path.join(root,'inputs/sodium-1.21.11.jar'),bytes.subarray(8,40).toString('base64')],{encoding:'utf8',timeout:10000,windowsHide:true}));bot._client.write('custom_payload',{channel:'aegisac:report_v1',data:Buffer.from(scan.report,'base64')});result.reportSent=true;}catch(e){result.error=e.message;finish(1);}
  }
});
bot.once('spawn',()=>{result.spawned=true;finish(0);});
bot.on('kicked',reason=>{result.kick=reason;finish(1);});
bot.on('error',e=>{result.error=e.message;finish(1);});
setTimeout(()=>{result.timeout=true;finish(1);},30000);
