const grpc = require('@grpc/grpc-js');
const protoLoader = require('@grpc/proto-loader');
const path = require('path');

const PROTO = path.join(__dirname, 'dynamic_min.proto');
const packageDefinition = protoLoader.loadSync(PROTO, {
  keepCase: true,
  longs: String,
  enums: String,
  defaults: true,
  oneofs: true,
});
const proto = grpc.loadPackageDefinition(packageDefinition).bilibili.app.dynamic.v2;

const dynamicId = process.argv[2] || '1217424289881391104';
const client = new proto.Dynamic('app.bilibili.com:443', grpc.credentials.createSsl());

const metadata = new grpc.Metadata();
metadata.add('user-agent', 'Dalvik/2.1.0 (Linux; U; Android 12; Pixel 6 Build/SQ3A.220705.004) 7.38.0 os/android model/Pixel 6 mobi_app/android build/7380300 channel/master innerVer/7380310 osVer/12 network/2');
metadata.add('x-bili-mid', '0');
metadata.add('x-bili-aurora-zone', '');
metadata.add('x-bili-trace-id', '0123456789abcdef0123456789ab:0123456789abcdef:0:0');
metadata.add('buvid', 'XY0000000000000000000000000000infoc');

client.DynDetail({ dynamic_id: dynamicId, local_time: 8 }, metadata, (err, res) => {
  if (err) {
    console.error('grpc error', err.code, err.details, err.message);
    process.exit(1);
  }
  console.log(JSON.stringify(res, null, 2).slice(0, 4000));
  const modules = res?.item?.modules || [];
  for (const m of modules) {
    if (m.module_type === 'module_author' && m.module_author) {
      console.log('IP:', m.module_author.ptime_location_text);
    }
  }
});
