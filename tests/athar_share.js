// Athar share test. Run via the CDP harness: part 1 (stub fonts, seed, open) then click share, then read the share frame.
// P0: document.fonts.load=function(){return Promise.resolve()}
// P1: localStorage.setItem('athar-v1',JSON.stringify([{id:'t1',s:40,v:60,t:'d',x:'هدعي في كل سجدة',ts:Date.now()}]));document.querySelector('#tileAthar').click()
// P2: document.querySelector('#view-athar .at-share').click()
// P3: expect ov=true, joined == note text only (no verse text, no tafsir), png data URL
