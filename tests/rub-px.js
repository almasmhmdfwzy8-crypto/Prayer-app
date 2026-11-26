(async()=>{const AR='٠١٢٣٤٥٦٧٨٩';const ar=n=>(''+n).split('').map(c=>AR[+c]).join('');
const bad=[];let nr=0,minGap=1e9,pg1=null;
for(let p=1;p<=604;p++){window.zaidMushafGo(p);let t=0;while(t<60){await new Promise(r=>setTimeout(r,8));const b=document.querySelector('#mqText .pxf b');if(b&&b.textContent===ar(p))break;t++;}await new Promise(r=>setTimeout(r,8));
const pg=document.querySelector('#mqText .pxp');const pr=pg.getBoundingClientRect();
const ws=[...pg.querySelectorAll('.w')].map(w=>w.getBoundingClientRect());
[...pg.querySelectorAll('.rub i,.sj')].forEach(r=>{nr++;const a=r.getBoundingClientRect();
 for(const w of ws){if(a.left<w.right&&a.right>w.left&&a.top<w.bottom&&a.bottom>w.top){bad.push([p,Math.round(a.top),Math.round(w.left),Math.round(w.top),Math.round(w.bottom)]);break;}}});
}
return JSON.stringify({rubs:nr,overlaps:bad.length,first:bad.slice(0,8)});})()
