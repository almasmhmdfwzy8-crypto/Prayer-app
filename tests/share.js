(async()=>{const w=ms=>new Promise(r=>setTimeout(r,ms));const out={},cap=[];
window.Capacitor={isNativePlatform:()=>true,Plugins:{Share:{share:async o=>{cap.push(o);}},Filesystem:{writeFile:async()=>({}),getUri:async()=>({uri:'file:///x.png'}),deleteFile:async()=>({})}}};
const LINK='تحميل التطبيق: https://github.com/almasmhmdfwzy8-crypto/Prayer-app/releases/latest/download/app-debug.apk';
const FR='من تطبيق زَادُ الْمُؤْمِنِ\n'+LINK;
out.frame=window.zaidWithLink('x')==='x\n'+FR;
/* dhikr with verified benefit, dhikr without one, dua with its source */
const tasbih=window.zaidDhText({t:'سُبْحَانَ اللَّهِ وَبِحَمْدِهِ',c:100},{note:'الْمَصْدَرُ الْأَسَاسِيُّ: حِصْنُ الْمُسْلِمِ لِلشَّيْخِ'});
out.dhikrFadl=tasbih.includes('فضله: قال النبي ﷺ: «من قال: سبحان الله وبحمده، في يوم مائة مرة، حطت خطاياه')&&tasbih.includes('البخاري ٦٤٠٥')&&tasbih.includes('(١٠٠ مرة)')&&tasbih.includes('المصدر: حِصْنُ الْمُسْلِمِ');
const noF=window.zaidDhText({t:'رَضِيتُ بِاللَّهِ رَبًّا، وَبِالْإِسْلَامِ دِينًا، وَبِمُحَمَّدٍ ﷺ نَبِيًّا',c:3},{note:'الْمَصْدَرُ الْأَسَاسِيُّ: حِصْنُ الْمُسْلِمِ'});
out.noFadlWhenUnverified=!noF.includes('فضله');
const dua=window.zaidDhText({t:'رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً',c:1,src:'الْبَقَرَةِ: 201'},{note:''});
out.duaSource=dua.includes('المصدر: الْبَقَرَةِ: 201')&&!dua.includes('فضله');
/* verse text: Muyassar quote is verbatim from the data, 1-2 sentences, source named */
const vt=await window.zaidVerseText(40,60);
const d=await (await fetch('td-data.json')).json();const src=d.m['40'][59].replace(/\s+/g,' ');
const q=(vt.match(/«([^»]+)»/)||[])[1]||'';
out.verseHasParts=vt.startsWith('﴿ ')&&/\[[^\]]+: ٦٠\]/.test(vt)&&vt.includes('من التفسير الميسر')&&vt.includes('مجمع الملك فهد')&&vt.endsWith(LINK);
out.quoteVerbatim=q.replace(/ \.\.\.$/,'').length>20&&src.includes(q.replace(/ \.\.\.$/,''));
const vt2=await window.zaidVerseText(2,89);const q2=(vt2.match(/«([^»]+)»/)||[])[1]||'';out.longQuoteShort=q2.length<=330;
cap.length=0;
/* buttons */
document.getElementById('fajrShare').click();await w(300);out.fajr=cap.pop();
document.querySelector('details.sira button').click();await w(300);out.sira=cap.pop();
const ws=document.getElementById('wirdShare');out.wirdBtn=!!ws;if(ws){ws.click();await w(300);out.wirdZeroNoShare=cap.length===0;}
out.podBtn=!!document.querySelector('#podNP [data-a="share"]');
const sd=document.getElementById('shareDay');sd.click();await w(2500);out.times=cap.pop();
window.zaidFajrCard(0);await w(1500);document.querySelector('#shareOv button, .share-pan button')&&0;
out.res={};
const keep={};['fajr','sira','wird','times'].forEach(k=>{const o=out[k];keep[k]=o&&o.text;});
return JSON.stringify({checks:Object.fromEntries(Object.entries(out).filter(([k,v])=>typeof v==='boolean')),texts:Object.assign(keep,{tasbih,dua,vt,vt2})});})()
