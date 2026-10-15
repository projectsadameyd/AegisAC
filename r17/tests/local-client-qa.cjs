// Local-only QA. Refuses every endpoint except this task's loopback test server.
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const fs = require('node:fs');
const path = require('node:path');
const mode=process.argv[2] || 'legitimate';
if(!['legitimate','remote'].includes(mode))throw Error('Use legitimate|remote');
const result={mode,protocol:'1.21.11',started:new Date().toISOString(),steps:[],kicked:null,errors:[],clientWorldSamples:[]};
const bot=mineflayer.createBot({host:'127.0.0.1',port:25581,username:mode==='remote'?'AegisRemoteQA':'AegisLegitQA',auth:'offline',version:'1.21.11',hideErrors:true});
// Mineflayer's movement loop does not emit the 1.21.11 end-of-client-tick packet.
// Supply it after its synchronous movement writes so this trace matches the modern protocol.
bot.on('physicsTick',()=>setImmediate(()=>{if(!finished&&spawned)bot._client.write('tick_end',{});}));
const wait=ms=>new Promise(r=>setTimeout(r,ms));
let spawned=false,finished=false;
function save(code){if(finished)return;finished=true;result.finished=new Date().toISOString();fs.mkdirSync(path.join(__dirname,'../results'),{recursive:true});fs.writeFileSync(path.join(__dirname,'../results',`${mode}-client.json`),JSON.stringify(result,null,2)+'\n');console.log(JSON.stringify(result));bot.end();setTimeout(()=>process.exit(code),200);}
bot.on('error',error=>{result.errors.push(error.message);if(!spawned)save(1);});
bot.on('kicked',reason=>{result.kicked=reason;if(mode==='remote'){const text=JSON.stringify(reason);result.guardKickVerified=text.includes('Remote Interaction Blocked')&&text.includes('AegisAC')&&text.includes('R17-');save(result.guardKickVerified?0:1);}else save(1);});
bot.once('spawn',async()=>{
    spawned=true;
    try {
        await wait(8000);
        result.spawn={x:bot.entity.position.x,y:bot.entity.position.y,z:bot.entity.position.z};
        result.steps.push('spawn and eight-second stabilization');
        if(mode==='remote') {
            const base=bot.entity.position.floored();
            for(let i=0;i<5&&!finished;i++) {
                bot._client.write('block_dig',{status:0,location:{x:base.x+20,y:base.y-1,z:base.z},face:1,sequence:i+1});
                result.steps.push('remote block request '+(i+1));
                await wait(500);
            }
            await wait(2000);if(!finished){result.errors.push('Expected R17 branded remote-interaction kick was not received');save(1);}return;
        }
        for(let x=12;x<=16;x++)for(let y=-58;y<=-54;y++)for(let z=12;z<=16;z++) {
            const b=bot.blockAt(new Vec3(x,y,z));if(b)result.clientWorldSamples.push({x,y,z,advertised:b.name});
        }
        result.obfuscatedInteriorSamples=result.clientWorldSamples.filter(b=>b.advertised!=='stone'&&!(b.x===14&&b.y===-56&&b.z===14)).length;
        if(result.clientWorldSamples.length!==125||result.obfuscatedInteriorSamples===0)throw Error('Anti-Xray fixture was not present or client chunk obfuscation was not observed');
        bot.setControlState('forward',true);await wait(2200);bot.clearControlStates();result.steps.push('walking');
        bot.setControlState('sprint',true);bot.setControlState('forward',true);await wait(1800);bot.clearControlStates();result.steps.push('sprinting');
        for(let i=0;i<3;i++){bot.setControlState('jump',true);await wait(450);bot.setControlState('jump',false);await wait(650);}result.steps.push('jumping and landing');
        bot.setControlState('sneak',true);bot.setControlState('right',true);await wait(1300);bot.clearControlStates();result.steps.push('sneaking and strafing');
        for(let i=0;i<9;i++){bot.setQuickBarSlot(i);await wait(150);}result.steps.push('all valid hotbar slots');
        for(let i=0;i<12;i++){await bot.look(i*Math.PI/6,Math.sin(i)*0.4,true);await wait(80);}result.steps.push('camera rotations');
        const here=bot.entity.position.floored();const floor=bot.blockAt(here.offset(1,-1,0));
        if(floor&&floor.name!=='air'&&bot.canDigBlock(floor)){await bot.dig(floor);result.steps.push('normal block break');}
        await wait(2500);result.finalPosition={x:bot.entity.position.x,y:bot.entity.position.y,z:bot.entity.position.z};save(0);
    }catch(error){result.errors.push(error.message);save(1);}
});
setTimeout(()=>{if(!finished){result.errors.push('Local QA timed out');save(1);}},45000);
