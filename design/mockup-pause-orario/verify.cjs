const { chromium } = require('playwright');
const assert = require('node:assert/strict');
const path = require('node:path');
const { pathToFileURL } = require('node:url');
(async()=>{
 const browser=await chromium.launch({headless:true});
 const page=await browser.newPage({viewport:{width:1280,height:1050},deviceScaleFactor:1});
 const errors=[]; page.on('pageerror',e=>errors.push(e.message));
 const shot=async name=>page.locator('.phone').screenshot({path:path.join(__dirname,name),animations:'disabled'});
 const pick=async(field,h,m)=>{
   await page.locator('#break-'+field).click();
   await page.locator('[data-h="'+h+'"]').click();
   await page.locator('[data-m="'+m+'"]').click();
   await page.locator('#confirm-wheel').click();
 };
 const net=async expected=>assert.equal(await page.locator('#net-total').textContent(),expected);
 try {
 await page.goto(pathToFileURL(path.join(__dirname,'index.html')).href);
 await net('19 h 00 min');await shot('01-orario.png');
 await page.locator('#shift-day [data-open]').click();
 await pick('start',12,30);await pick('end',13,0);await shot('02-aggiungi-pausa.png');
 await page.locator('#save-break').click();await net('18 h 30 min');
 assert.equal(await page.locator('#break-total').textContent(),'0 h 30 min');await shot('03-pausa-salvata.png');
 await page.locator('#shift-day [data-open]').click();await pick('start',12,45);await pick('end',13,15);
 await page.locator('#save-break').click();assert.match(await page.locator('#error').textContent(),/sovrappone/);await shot('04-sovrapposizione.png');
 await page.locator('#cancel-editor').click();await net('18 h 30 min');
 await page.locator('#shift-day [data-open]').click();await pick('start',5,30);await pick('end',6,0);
 await page.locator('#save-break').click();assert.match(await page.locator('#error').textContent(),/compresa nel turno/);await page.locator('#cancel-editor').click();
 await page.locator('#shift-night [data-open]').click();await page.locator('#save-break').click();await net('18 h 00 min');
 assert.equal(await page.locator('#night-total').textContent(),'5 h 30 min');await shot('05-notturno.png');
 await page.locator('#shift-day [data-edit]').click();await page.locator('#break-start').click();
 await page.locator('[data-h="9"]').click();await page.locator('#cancel-wheel').click();
 assert.equal(await page.locator('#break-start b').textContent(),'12:30');
 await page.locator('#remove-break').click();await page.locator('#confirm-delete').click();await net('18 h 30 min');
 await page.locator('#extra').click();await page.locator('#save-shift').click();assert.match(await page.locator('#error').textContent(),/presenti/);
 await pick('start',18,0);await pick('end',20,0);await page.locator('#save-shift').click();await net('20 h 30 min');
 assert.equal(await page.locator('.shift').count(),4);
 await page.locator('#shift-extra-3 [data-shift]').click();await pick('start',16,0);await pick('end',19,0);await page.locator('#save-shift').click();await net('19 h 30 min');
 await page.locator('#theme').click();await shot('06-tema-scuro.png');
 await page.setViewportSize({width:375,height:850});
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth),375);
 await page.locator('.phone').screenshot({path:path.join(__dirname,'07-mobile.png')});
 assert.deepEqual(errors,[]);
 console.log('PASS: pauses, net totals, overnight/night hours, overlaps, bounds, cancel, delete, overtime, themes, mobile.');
 }finally{await browser.close()}
})().catch(e=>{console.error(e);process.exit(1)});
