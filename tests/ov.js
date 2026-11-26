(async()=>{const W=innerWidth;const res={};
const clipped=(e)=>{for(let p=e.parentElement;p&&p!==document.body;p=p.parentElement){const o=getComputedStyle(p);if(/(hidden|auto|scroll|clip)/.test(o.overflowX)&&p.clientWidth<=W+1)return true;}return false;};
for(const v of ['prayer','yomak','adhkar','mushaf','fajr','sira','settings','lib','pod']){
 try{window.__showView(v);}catch(e){}
 await new Promise(r=>setTimeout(r,700));
 const bad=[];const root=document.getElementById('view-'+v)||document.body;
 [...root.querySelectorAll('*')].forEach(e=>{const r=e.getBoundingClientRect();if(!r.width||getComputedStyle(e).position==='fixed')return;if((r.right>W+1||r.left<-1)&&!clipped(e))bad.push((e.tagName+'.'+(e.className&&e.className.baseVal===undefined?e.className:'')).slice(0,50)+' '+Math.round(r.left)+'..'+Math.round(r.right));});
 res[v]={sw:document.documentElement.scrollWidth,bw:document.body.scrollWidth,vis:root.offsetParent!==null||getComputedStyle(root).display!=='none',bad:bad.slice(0,6),n:bad.length};}
return JSON.stringify(res);})()
