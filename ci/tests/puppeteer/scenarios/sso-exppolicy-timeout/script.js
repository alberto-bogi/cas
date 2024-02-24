const puppeteer = require("puppeteer");
const cas = require("../../cas.js");

(async () => {
    const browser = await puppeteer.launch(cas.browserOptions());
    const page = await cas.newPage(browser);
    await cas.gotoLogin(page);
    await cas.loginWith(page);
    await cas.assertCookie(page);

    for (let i = 0; i < 3; i++) {
        await cas.log(`Attempt #${i}: waiting for timeout to complete...`);
        await cas.gotoLogin(page, "https://apereo.github.io");
        await cas.waitForTimeout(page, 1000);
        await cas.assertTicketParameter(page);
        await cas.gotoLogin(page);
        await cas.waitForTimeout(page, 1000);
        await cas.assertCookie(page);
    }
    await cas.waitForTimeout(page, 4000);
    await cas.gotoLogin(page);
    await cas.assertCookie(page, false);
    await browser.close();
})();
