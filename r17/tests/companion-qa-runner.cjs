const {spawnSync}=require('node:child_process'),path=require('node:path');
const modes=process.argv.slice(2);if(!modes.length)throw Error('Specify local QA modes');
let failed=false;
for(const mode of modes){
  const p=spawnSync(process.execPath,[path.join(__dirname,'companion-join-qa.cjs'),mode],{encoding:'utf8',timeout:60000,windowsHide:true});
  let result;try{result=JSON.parse(p.stdout.trim());}catch{result={errors:[p.stdout,p.stderr,p.error?.message].filter(Boolean)};}
  console.log(JSON.stringify({mode,exit:p.status,spawned:result.spawned,challenge:result.challenge,authenticated:result.authenticated,errors:result.errors}));
  if(p.status!==0)failed=true;
}
process.exitCode=failed?1:0;
