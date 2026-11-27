(async()=>{const w=ms=>new Promise(r=>setTimeout(r,ms));const out={};
/* simulate the phone: the service says the radio is still playing after the app was reopened */
window.Capacitor={Plugins:{AdhanNotifier:{radioStatus:()=>Promise.resolve({playing:true}),addListener:()=>{}}}};
out.beforeOn=document.body.classList.contains('rr-on');
window.__rrSync();await w(300);out.afterSyncOn=document.body.classList.contains('rr-on');
window.Capacitor.Plugins.AdhanNotifier.radioStatus=()=>Promise.resolve({playing:false});
window.__rrSync();await w(300);out.afterStopOff=!document.body.classList.contains('rr-on');
return JSON.stringify(out);})()
