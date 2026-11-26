(async()=>{const AR='٠١٢٣٤٥٦٧٨٩';const ar=n=>(''+n).split('').map(c=>AR[+c]).join('');
const out=[];for(let p=1;p<=604;p+=1){window.zaidMushafGo(p);let t=0;while(t<60){await new Promise(r=>setTimeout(r,8));const b=document.querySelector('#mqText .pxf b');if(b&&b.textContent===ar(p))break;t++;}await new Promise(r=>setTimeout(r,8));
const pg=document.querySelector('#mqText .pxp');const ls=[...pg.querySelectorAll('.ln')];const f=parseFloat(ls[0].style.fontSize);out.push([p,+f.toFixed(1),ls.length,Math.round(pg.clientHeight),Math.round(pg.clientWidth)]);}
const h=out.find(x=>x[0]===5);const fs=out.map(x=>x[1]);
const smaller=out.filter(x=>x[2]>=15&&x[1]<h[1]-0.5).length;
const hist={};out.forEach(x=>{const k=Math.floor(x[1]/2)*2;hist[k]=(hist[k]||0)+1});
return JSON.stringify({p5:h,min:Math.min(...fs),max:Math.max(...fs),smaller_than_p5:smaller,hist,p1:out[0],p2:out[1],p27:out[26],p604:out[603],pg:out.slice(0,12).map(x=>x[1])});})()
