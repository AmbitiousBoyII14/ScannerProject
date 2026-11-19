"""Exercise app-generated VLESS outbounds with a trusted local CA; replace only TUN ingress."""
import contextlib, http.server, json, os, pathlib, secrets, socket, subprocess, sys, threading, time

work=pathlib.Path(sys.argv[1]);proof=secrets.token_hex(16).encode()
class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200);self.end_headers();self.wfile.write(proof)
    def log_message(self,*args):pass

@contextlib.contextmanager
def core(name,cfg,port):
    path=work/(name+'.json');path.write_text(json.dumps(cfg));proc=subprocess.Popen([work/'xray','run','-c',path],env={**os.environ,'SSL_CERT_FILE':str(work/'cert.pem')},stderr=subprocess.STDOUT)
    try:
        for _ in range(100):
            if proc.poll() is not None:raise RuntimeError('Fixture exited: '+name)
            try:
                with socket.create_connection(('127.0.0.1',port),.1):break
            except OSError:time.sleep(.05)
        else:raise RuntimeError('Fixture not listening: '+name)
        yield
    finally:
        proc.terminate();proc.wait(timeout=10)

with http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler) as dest:
    threading.Thread(target=dest.serve_forever,daemon=True).start()
    for i,transport in enumerate(['tcp','ws']):
        client=json.loads((work/f'client{i}.json').read_text());out=client['outbounds'][0];serverport=22262+i
        assert out['protocol']=='vless' and out['streamSettings']['security']=='tls'
        stream={'network':transport,'security':'tls','tlsSettings':{'certificates':[{'certificateFile':str(work/'cert.pem'),'keyFile':str(work/'key.pem')}]}}
        if transport=='ws':stream['wsSettings']={'path':'/fixture'}
        server={'inbounds':[{'listen':'127.0.0.1','port':serverport,'protocol':'vless','streamSettings':stream,'settings':{'decryption':'none','clients':[{'id':'00000000-0000-4000-8000-000000000001'}]}}],'outbounds':[{'protocol':'freedom'}]}
        # This release blocks private destinations on a VLESS server by default. Permit only
        # our ephemeral HTTP proof socket; all other destinations remain blocked.
        server['outbounds'][0]['settings']={'finalRules':[{'action':'allow','network':'tcp','ip':['127.0.0.1/32'],'port':str(dest.server_port)},{'action':'block'}]}
        for cfg in [client,server]:cfg['log']={'loglevel':'debug'}
        client['inbounds']=[{'listen':'127.0.0.1','port':22265,'protocol':'socks','settings':{'auth':'noauth'}}]
        with core('server',server,serverport),core('client',client,22265):
            output=subprocess.check_output(['curl','--fail','--silent','--show-error','--max-time','8','--noproxy','','--proxy','socks5h://127.0.0.1:22265',f'http://127.0.0.1:{dest.server_port}/proof'])
            assert output==proof,'Wrong fixture response';print('Verified VLESS outbound:',transport,'+ TLS')
    dest.shutdown()
