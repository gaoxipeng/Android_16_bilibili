const crypto = require('crypto');
const https = require('https');

const APP_KEY = '783bbb7264451d82';
const APP_SECRET = '2653583c8873dea268ab9386918b1d65';
const UA = 'Mozilla/5.0 (Linux; Android 12; Pixel 6 Build/SQ3A.220705.004) Mobile';

function appSign(params) {
  const query = Object.keys(params).sort().map((k) => `${k}=${params[k]}`).join('&');
  return crypto.createHash('md5').update(query + APP_SECRET).digest('hex');
}

function request(method, url, headers = {}, body = null) {
  return new Promise((resolve, reject) => {
    const u = new URL(url);
    const req = https.request({
      method,
      hostname: u.hostname,
      path: u.pathname + u.search,
      headers,
    }, (res) => {
      let data = '';
      res.on('data', (c) => (data += c));
      res.on('end', () => {
        try { resolve({ status: res.statusCode, json: JSON.parse(data), raw: data.slice(0, 500) }); }
        catch { resolve({ status: res.statusCode, raw: data.slice(0, 500) }); }
      });
    });
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

async function main() {
  const cookie = process.argv[2];
  if (!cookie) {
    console.log('Usage: node probe_passport_token.js "SESSDATA=...; bili_jct=...; DedeUserID=..."');
    return;
  }

  const ts = Math.floor(Date.now() / 1000).toString();
  const endpoints = [
    ['GET', `https://passport.bilibili.com/api/v2/oauth2/info?appkey=${APP_KEY}&ts=${ts}&sign=${appSign({ appkey: APP_KEY, ts })}`],
    ['GET', `https://passport.bilibili.com/x/passport-login/oauth2/info?appkey=${APP_KEY}&ts=${ts}&sign=${appSign({ appkey: APP_KEY, ts })}`],
    ['POST', 'https://passport.bilibili.com/x/passport-login/oauth2/info', `appkey=${APP_KEY}&ts=${ts}&sign=${appSign({ appkey: APP_KEY, ts })}`],
    ['POST', 'https://passport.bilibili.com/x/passport-login/oauth2/cookie/info', `appkey=${APP_KEY}&ts=${ts}&sign=${appSign({ appkey: APP_KEY, ts })}`],
    ['POST', 'https://passport.bilibili.com/x/passport-login/oauth2/cookie2token', `appkey=${APP_KEY}&ts=${ts}&sign=${appSign({ appkey: APP_KEY, ts })}`],
    ['POST', 'https://passport.bilibili.com/x/passport-login/oauth2/secret/info', `appkey=${APP_KEY}&ts=${ts}&sign=${appSign({ appkey: APP_KEY, ts })}`],
  ];

  for (const [method, url, body] of endpoints) {
    const headers = {
      'User-Agent': UA,
      Cookie: cookie,
      Referer: 'https://www.bilibili.com/',
    };
    if (body) {
      headers['Content-Type'] = 'application/x-www-form-urlencoded';
      headers['Content-Length'] = Buffer.byteLength(body);
    }
    const res = await request(method, url, headers, body);
    console.log('\n', method, url.split('passport.bilibili.com')[1].slice(0, 60));
    console.log('status', res.status, res.json ? JSON.stringify(res.json).slice(0, 300) : res.raw);
  }
}

main().catch(console.error);
