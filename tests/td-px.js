(async()=>{const w=ms=>new Promise(r=>setTimeout(r,ms));const out={};
window.zaidMushafGo(14,2,89);await w(1500);
const sp=document.querySelector('#mqText [data-a="2:89"]');out.hasSpan=!!sp;
sp.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true}));await w(5000);
const sh=document.getElementById('tdSheet'),pn=document.getElementById('tdPanel');
out.open=!sh.hidden;out.noHScroll=pn.scrollWidth<=pn.clientWidth+1;out.pageNoHScroll=document.documentElement.scrollWidth<=window.innerWidth+1;
out.touchAction=getComputedStyle(pn).touchAction;
const secs=[...pn.querySelectorAll('.td-sec')].map(s=>s.querySelector('h4').textContent+'|'+(s.querySelector('.td-src')||{}).textContent+'|'+s.querySelector('.td-body').textContent.slice(0,60).replace(/\n/g,' '));
out.secs=secs;
window.__handleBack();out.closedByBack=sh.hidden;
return JSON.stringify(out);})()
