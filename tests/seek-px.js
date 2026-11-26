(async()=>{const P=window.__pod,au=P.au,st=P.state;const sl=ms=>new Promise(r=>setTimeout(r,ms));
st.cur={t:'حلقة تجريبية',u:location.origin+'/t.mp3',d:120,show:{n:'برنامج',c:0}};st.queue=[st.cur];st.qi=0;au.src=st.cur.u;au.load();au.play().catch(e=>0);await sl(1500);
document.getElementById('podNP').hidden=false;const np=document.getElementById('podNP');const w=np.querySelector('.pd-wave');const r=w.getBoundingClientRect();
const ev=(el,t,x)=>el.dispatchEvent(new PointerEvent(t,{clientX:x,clientY:r.top+10,pointerId:1,bubbles:true}));
const out={dur:au.duration,seekable:au.seekable.length?au.seekable.end(0):0,t0:au.currentTime};
ev(w,'pointerdown',r.left+r.width*0.2);ev(w,'pointermove',r.left+r.width*0.5);ev(w,'pointermove',r.left+r.width*0.75);ev(w,'pointerup',r.left+r.width*0.75);await sl(1500);
out.afterDrag75=au.currentTime;out.paused=au.paused;
ev(w,'pointerdown',r.left+r.width*0.25);ev(w,'pointerup',r.left+r.width*0.25);await sl(1500);out.afterTap25=au.currentTime;
np.hidden=true;const mini=document.querySelector('.pd-mini');mini.hidden=false;const b=mini.querySelector('.bar');const rb=b.getBoundingClientRect();
const e2=(t,x)=>b.dispatchEvent(new PointerEvent(t,{clientX:x,clientY:rb.top+3,pointerId:2,bubbles:true}));e2('pointerdown',rb.left+rb.width*0.9);e2('pointerup',rb.left+rb.width*0.9);await sl(1500);out.miniTap90=au.currentTime;out.npOpened=!np.hidden;
return JSON.stringify(out);})()
