#!/usr/bin/env python3
"""relay.py <out.jsonl> [port]: a stand-in for the broadcast relay on 127.0.0.1. Records every message the desktop
(role=broadcast) sends, one JSON line each with the arrival time, and checks the key is present. Local only."""
import asyncio,json,sys,time,urllib.parse
import websockets
out=open(sys.argv[1],'w'); port=int(sys.argv[2]) if len(sys.argv)>2 else 9004
async def handler(ws):
    q=urllib.parse.parse_qs(urllib.parse.urlparse(ws.request.path).query)
    out.write(json.dumps({'t':time.time(),'event':'connect','role':q.get('role',[''])[0],'key':bool(q.get('key',[''])[0])})+'\n'); out.flush()
    try:
        async for m in ws:
            out.write(json.dumps({'t':time.time(),'msg':m if isinstance(m,str) else f'<binary {len(m)}>'})+'\n'); out.flush()
    finally:
        out.write(json.dumps({'t':time.time(),'event':'close'})+'\n'); out.flush()
async def main():
    async with websockets.serve(handler,'127.0.0.1',port,max_size=2**24):
        await asyncio.Future()
asyncio.run(main())
