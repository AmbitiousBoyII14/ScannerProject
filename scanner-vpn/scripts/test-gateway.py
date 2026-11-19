"""Loopback-only TLS / WS / WSS adapters to the disposable CI sshd."""
import asyncio,ssl,sys,pathlib
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
async def main():
    d=pathlib.Path(sys.argv[1]);ctx=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);ctx.load_cert_chain(d/'cert.pem',d/'key.pem')
    async with serve(ws,'127.0.0.1',22258),serve(ws,'127.0.0.1',22259,ssl=ctx),await asyncio.start_server(tls,'127.0.0.1',22260,ssl=ctx):
        (d/'ready').touch();await asyncio.Future()
asyncio.run(main())
