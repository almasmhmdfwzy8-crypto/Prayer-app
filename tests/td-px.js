(async()=>{const w=ms=>new Promise(r=>setTimeout(r,ms));const out={};
window.zaidMushafGo(14,2,89);await w(1500);
const sp=document.querySelector('#mqText [data-a="2:89"]');out.hasSpan=!!sp;
const sh=document.getElementById('tdSheet'),pn=document.getElementById('tdPanel'),box=document.getElementById('mqText');
const r=sp.getBoundingClientRect(),x=r.left+r.width/2,y=r.top+r.height/2;
const tc=(t,dx)=>{const T=new Touch({identifier:1,target:sp,clientX:x+(dx||0),clientY:y});sp.dispatchEvent(new TouchEvent(t,{bubbles:true,cancelable:true,touches:t==='touchend'?[]:[T],changedTouches:[T]}));};
const clk=()=>sp.dispatchEvent(new MouseEvent('click',{bubbles:true,cancelable:true,clientX:x,clientY:y}));
const chrome=()=>document.body.classList.contains('mq-chrome');
/* 1 normal tap: no sheet, Mushaf bar toggles */
const c0=chrome();tc('touchstart');await w(100);tc('touchend');clk();await w(300);
out.tapOpensSheet=!sh.hidden;out.tapToggledChrome=chrome()!==c0;
/* 2 long press: sheet opens, the click after it does not toggle the bar */
await w(800);const c1=chrome();
tc('touchstart');await w(700);out.longOpens=!sh.hidden;tc('touchend');clk();await w(300);
out.barUnchangedByLongPress=chrome()===c1;
/* 3 old verse card does not open together with the sheet */
out.oldCardHidden=document.getElementById('selCard')?document.getElementById('selCard').hidden:'n/a';
await w(4500);
out.noHScroll=pn.scrollWidth<=pn.clientWidth+1;out.pageNoHScroll=document.documentElement.scrollWidth<=window.innerWidth+1;
out.touchAction=getComputedStyle(pn).touchAction;
out.secs=[...pn.querySelectorAll('.td-sec')].map(s=>s.querySelector('h4').textContent+'|'+(s.querySelector('.td-src')||{}).textContent.slice(0,30));
window.__handleBack();out.closedByBack=sh.hidden;
/* 4 long press with movement (swipe) does not open */
await w(300);tc('touchstart');tc('touchmove',40);await w(700);out.moveCancels=sh.hidden;tc('touchend');
return JSON.stringify(out);})()
