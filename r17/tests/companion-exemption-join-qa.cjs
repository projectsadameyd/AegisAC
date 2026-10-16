// Runs only against the isolated Paper/AuthMe loopback test server.
const mineflayer=require('mineflayer'),fs=require('node:fs'),path=require('node:path'),{execFileSync}=require('node:child_process');
const {writeAction}=require('./dialog-qa-wire.cjs');
const mode=process.argv[2],label=process.argv[3]||mode;
if(!['exempt','required','clean'].includes(mode)||!/^[a-z0-9-]+$/.test(label))throw Error('Invalid QA arguments');
const root=path.resolve(__dirname,'..');
const result={mode,spawned:false,challenge:false,aegisDialog:false,authDialog:false,requiredDialog:false,errors:[]};
const username=mode==='clean'?'AegisCleanQA':'ExemptJoinQA';
const bot=mineflayer.createBot({host:'127.0.0.1',port:25581,version:'1.21.11',auth:'offline',username,hideErrors:true});
let finished=false;
function finish(code){if(finished)return;finished=true;fs.mkdirSync(path.join(root,'results/companion'),{recursive:true});fs.writeFileSync(path.join(root,`results/companion/hotfix3-${label}.json`),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));bot.end();setTimeout(()=>process.exit(code),200);}
bot._client.on('state',state=>{if(state==='configuration'&&mode==='clean')setImmediate(()=>bot._client.write('custom_payload',{channel:'minecraft:register',data:Buffer.from('aegisac:challenge_v1')}));});
bot._client.on('packet',(data,meta)=>{
    if(meta.name==='show_dialog'){
        const text=JSON.stringify(data);
        if(text.includes('aegisac:leave_verification'))result.aegisDialog=true;
        if(text.includes('Companion required')){result.requiredDialog=true;writeAction(bot._client,'aegisac:leave_verification');}
        const action=text.includes('authme:prejoin-register/submit')?'authme:prejoin-register/submit':text.includes('authme:prejoin-login/submit')?'authme:prejoin-login/submit':null;
        if(action){result.authDialog=true;writeAction(bot._client,action,{type:'compound',name:'',value:{password:{type:'string',value:'R17qaOnly8236!'},confirm:{type:'string',value:'R17qaOnly8236!'}}});}
    }
    if(meta.name==='custom_payload'&&data.channel==='aegisac:challenge_v1'){
        result.challenge=true;
        if(mode!=='clean'){result.errors.push('Exempt/missing client unexpectedly received a challenge');finish(1);return;}
        try{
            const jar=path.join(root,'build/AegisAC-R17-Companion-preview2-hotfix3-1.21.11.jar');
            const scan=JSON.parse(execFileSync('java',['-cp',jar,path.join(__dirname,'CompanionScanCli.java'),path.join(root,'inputs/sodium-1.21.11.jar'),Buffer.from(data.data).subarray(8,40).toString('base64')],{encoding:'utf8',timeout:10000,windowsHide:true}));
            bot._client.write('custom_payload',{channel:'aegisac:report_v1',data:Buffer.from(scan.report,'base64')});
        }catch(error){result.errors.push(error.message);finish(1);}
    }
});
bot.once('spawn',()=>{
    result.spawned=true;
    const okay=mode==='exempt'?!result.challenge&&!result.aegisDialog&&result.authDialog:mode==='clean'&&result.challenge&&result.authDialog;
    if(!okay)result.errors.push('Unexpected world admission behavior');finish(okay?0:1);
});
bot.on('kicked',reason=>{result.aegisKick=JSON.stringify(reason).includes('AegisAC');const okay=mode==='required'&&!result.spawned&&result.requiredDialog&&result.aegisKick;finish(okay?0:1);});
bot.on('error',error=>{result.errors.push(error.message);finish(1);});
setTimeout(()=>{result.errors.push('Admission QA timed out');finish(1);},25000);
