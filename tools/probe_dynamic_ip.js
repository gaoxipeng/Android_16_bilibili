const crypto = require('crypto');
const https = require('https');

const UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';
const MIXIN_ORDER = [46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,33,9,42,19,29,28,14,39,12,38,41,13,37,48,7,16,24,55,40,61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,52,11,36,20,34,44,6];

function get(url, headers = {}) {
  return new Promise((resolve, reject) => {
    https.get(url, { headers: { 'User-Agent': UA, ...headers } }, (res) => {
      let data = '';
      res.on('data', (c) => (data += c));
      res.on('end', () => {
        try { resolve(JSON.parse(data)); } catch { resolve(data.slice(0, 300)); }
      });
    }).on('error', reject);
  });
}

function deriveMixinKey(imgUrl, subUrl) {
  const token = (url) => url.split('/').pop().split('.')[0];
  const raw = token(imgUrl) + token(subUrl);
  return MIXIN_ORDER.map((i) => raw[i]).join('').slice(0, 32);
}

function sign(params, mixinKey) {
  const p = { ...params, wts: Math.floor(Date.now() / 1000).toString() };
  const query = Object.keys(p).sort().map((k) => `${encodeURIComponent(k)}=${encodeURIComponent(p[k])}`).join('&');
  p.w_rid = crypto.createHash('md5').update(query + mixinKey).digest('hex');
  return p;
}

function qs(params) {
  return Object.entries(params).map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&');
}

async function main() {
  const spi = await get('https://api.bilibili.com/x/frontend/finger/spi');
  const buvid3 = spi?.data?.b_3;
  const cookie = buvid3 ? `buvid3=${buvid3}` : '';

  const nav = await get('https://api.bilibili.com/x/web-interface/nav', { Cookie: cookie });
  const wbi = nav?.data?.wbi_img;
  const mixinKey = deriveMixinKey(wbi.img_url, wbi.sub_url);

  const mid = process.argv[2] || '2';
  const feedParams = sign({
    host_mid: mid,
    timezone_offset: '-480',
    platform: 'h5',
    gaia_source: 'main_app',
    features: 'itemOpusStyle,listOnlyfans,opusBigCover,onlyfansVote,forwardListHidden,decorationCard,commentsNewVersion,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,endFooterHidden',
    web_location: '333.1365',
  }, mixinKey);

  const feed = await get(`https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/space?${qs(feedParams)}`, {
    Cookie: cookie,
    Referer: `https://space.bilibili.com/${mid}/dynamic`,
    'x-bili-device-req-json': '{"platform":"android","device":"phone","mobi_app":"android","build":8510300}',
  });
  console.log('feed code', feed.code, feed.message);
  const items = feed?.data?.items || [];
  console.log('items', items.length);
  for (const it of items.slice(0, 8)) {
    const a = it?.modules?.module_author || {};
    console.log(it.id_str, a.name, JSON.stringify({
      pub_location_text: a.pub_location_text,
      ptime_location_text: a.ptime_location_text,
      pub_time: a.pub_time,
      label: a.label,
    }));
  }

  if (items[0]?.id_str) {
    const id = items[0].id_str;
    for (const [label, baseUrl, params] of [
      ['detail-wbi', 'https://api.bilibili.com/x/polymer/web-dynamic/v1/detail', sign({
        id,
        timezone_offset: '-480',
        platform: 'web',
        gaia_source: 'main_web',
        features: 'itemOpusStyle,opusBigCover,onlyfansVote,endFooterHidden,decorationCard,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,commentsNewVersion',
        web_location: '333.1368',
      }, mixinKey)],
      ['detail-desktop', 'https://api.bilibili.com/x/polymer/web-dynamic/desktop/v1/detail', sign({
        id,
        timezone_offset: '-480',
        platform: 'web',
        features: 'itemOpusStyle,opusBigCover,onlyfansVote,endFooterHidden,decorationCard,onlyfansAssetsV2,ugcDelete,onlyfansQaCard,commentsNewVersion',
        web_location: '333.1368',
      }, mixinKey)],
    ]) {
      const detail = await get(`${baseUrl}?${qs(params)}`, {
        Cookie: cookie,
        Referer: `https://t.bilibili.com/${id}`,
        'x-bili-device-req-json': '{"platform":"android","device":"phone","mobi_app":"android","build":8510300}',
      });
      const a = detail?.data?.item?.modules?.module_author || {};
      console.log('\n', label, 'code', detail.code, 'pub_location_text', JSON.stringify(a.pub_location_text), 'keys', Object.keys(a).join(','));
    }
  }
}

main().catch(console.error);
