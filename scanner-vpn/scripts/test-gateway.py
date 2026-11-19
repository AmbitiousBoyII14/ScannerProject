"""Loopback-only TLS / WS / WSS adapters to the disposable CI sshd."""
import asyncio,ssl,sys,pathlib,os
from websockets.asyncio.server import serve

async def relay(up,down):
    tasks=[asyncio.create_task(up),asyncio.create_task(down)]
    await asyncio.wait(tasks,return_when=asyncio.FIRST_COMPLETED)
    for t in tasks:t.cancel()
    await asyncio.gather(*tasks,return_exceptions=True)
async def ws(gateway):
    r,w=await asyncio.open_connection('127.0.0.1',22257)
    async def upload():
        async for b in gateway:w.write(b);await w.drain()
    async def download():
        while b:=await r.read(65536):await gateway.send(b)
    try:await relay(upload(),download())
    finally:w.close()
async def tls(client,peer):
    r,w=await asyncio.open_connection('127.0.0.1',22257)
    async def copy(a,b):
        while data:=await a.read(65536):b.write(data);await b.drain()
    try:await relay(copy(client,w),copy(r,peer))
    finally:w.close();peer.close()
async def raw_upgrade(reader,writer):
    request=await reader.readuntil(b'\r\n\r\n')
    if not request.startswith(b'GET /ssh HTTP/1.1\r\n') or b'x-fixture: proof' not in request.lower():
        writer.write(b'HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n');await writer.drain();writer.close();return
    writer.write(b'HTTP/1.1 101 Switching Protocols\r\nConnection: Upgrade\r\nUpgrade: ssh\r\n\r\n');await writer.drain();await tls(reader,writer)
async def ws_to_raw(reader,writer):
    # A normal v13 request, but the response switches directly to an SSH byte stream.
    request=(await reader.readuntil(b'\r\n\r\n')).lower()
    if b'sec-websocket-key:' not in request or b'sec-websocket-version: 13' not in request:
        writer.close();return
    writer.write(b'HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n');await writer.drain();await tls(reader,writer)

def enforce_route(conn,request):
    if request.path!='/ssh':return conn.respond(404,'Not Found')
    if request.headers.get('Sec-WebSocket-Protocol')!='binary':return conn.respond(426,'Binary subprotocol required')

async def proof(reader,writer):
    try:
        await asyncio.wait_for(reader.readuntil(b'\r\n\r\n'),4)
        writer.write(b'HTTP/1.1 204 No Content\r\nConnection: close\r\nContent-Length: 0\r\n\r\n');await writer.drain()
    finally:writer.close()
async def main():
    d=pathlib.Path(sys.argv[1]);ctx=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);ctx.load_cert_chain(d/'cert.pem',d/'key.pem')
    async with serve(ws,'127.0.0.1',22258),serve(ws,'127.0.0.1',22259,ssl=ctx),serve(ws,'127.0.0.1',22267,ssl=ctx,subprotocols=['binary'],process_request=enforce_route),await asyncio.start_server(proof,'127.0.0.1',22268),await asyncio.start_server(tls,'127.0.0.1',22260,ssl=ctx),await asyncio.start_server(ws_to_raw,'127.0.0.1',22270,ssl=ctx),await asyncio.start_server(raw_upgrade,'127.0.0.1',22261,ssl=ctx):
        if os.environ.get('DEVICE_PROOF'):
            protected=await asyncio.start_server(proof,'192.0.2.148',22269)
        (d/'ready').touch();await asyncio.Future()
asyncio.run(main())
