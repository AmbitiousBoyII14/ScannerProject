#!/usr/bin/env python3
"""Offline RSA signing for Android v2 (minSdk 24). CI holds only the public cert.
Only Gradle-produced unsigned, non-ZIP64 APKs without comments are accepted.
Android SDK apksigner independently verifies every published result.
"""
import base64, hashlib, json, os, pathlib, struct, subprocess, sys

root = pathlib.Path(__file__).resolve().parents[1]
cert = root / 'signing/update-cert.pem'
approval = root / 'signing/approval.json'
sha = lambda b: hashlib.sha256(b).digest()
u32 = lambda n: struct.pack('<I', n)
u64 = lambda n: struct.pack('<Q', n)
lp = lambda b: u32(len(b)) + b
alg = u32(0x0103)  # RSA PKCS#1 v1.5 with SHA-256

def openssl(*args, data=None):
    return subprocess.check_output(['openssl', *map(str,args)], input=data)

def prepare(apk):
    raw = apk.read_bytes(); eocd = raw[-22:]
    if eocd[:4] != b'PK\x05\x06' or eocd[-2:] != b'\0\0': raise ValueError('Unsupported zip')
    offset = struct.unpack_from('<I', eocd, 16)[0]; directory = raw[offset:-22]
    if directory[:4] != b'PK\x01\x02' or raw[offset-16:offset] == b'APK Sig Block 42': raise ValueError('Expected unsigned APK')
    chunks = []
    for part in (raw[:offset], directory, eocd):
        for i in range(0,len(part),1<<20):
            chunk=part[i:i+(1<<20)]; chunks.append(sha(b'\xa5'+u32(len(chunk))+chunk))
    digest=sha(b'\x5a'+u32(len(chunks))+b''.join(chunks))
    data=lp(lp(alg+lp(digest)))+lp(lp(openssl('x509','-in',cert,'-outform','DER')))+lp(b'')
    return raw,offset,data

def main():
    action=sys.argv[1];folder=pathlib.Path(sys.argv[2]);apks=sorted(folder.glob('Scanner-VPN-*.apk')); assert len(apks)==4
    requests={};signed={};key=root/'.private/update-key.pem'
    for apk in apks:
        raw,at,data=prepare(apk); h=sha(data);requests[apk.name]=h.hex()
        if action=='attach':
            sig=base64.b64decode(json.loads(approval.read_text())['signatures'][apk.name],validate=True)
            pub=openssl('x509','-in',cert,'-pubkey','-noout');pub=openssl('pkey','-pubin','-outform','DER',data=pub)
            signer=lp(data)+lp(lp(alg+lp(sig)))+lp(pub)
            pair=u32(0x7109871a)+lp(lp(signer)); pairs=u64(len(pair))+pair;size=u64(len(pairs)+24)
            block=size+pairs+size+b'APK Sig Block 42'
            apk.write_bytes(raw[:at]+block+raw[at:-6]+u32(at+len(block))+raw[-2:])
        elif action!='request': raise ValueError(action)
    if action=='request':print('::notice title=Offline signing request::'+json.dumps(requests,separators=(',',':')))

if __name__=='__main__':
    if sys.argv[1]=='approve':
        # Operator supplies the reviewed build run and four SHA-256 signing requests.
        req=json.load(sys.stdin); assert len(req)==4
        sigs={k:base64.b64encode(openssl('pkeyutl','-sign','-inkey',root/'.private/update-key.pem','-pkeyopt','digest:sha256',data=bytes.fromhex(v))).decode() for k,v in req.items()}
        approval.write_text(json.dumps(dict(run=sys.argv[2],signatures=sigs),indent=2)+'\n')
    else: main()
