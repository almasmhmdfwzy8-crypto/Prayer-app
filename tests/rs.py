import http.server,os,re,sys
class H(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        path=self.translate_path(self.path.split('?')[0])
        rng=self.headers.get('Range')
        if path.endswith('.mp3') and os.path.isfile(path) and not self.path.startswith('/norange'):
            d=open(path,'rb').read();n=len(d);a,b=0,n-1;code=200
            if rng:
                m=re.match(r'bytes=(\d+)-(\d*)',rng);a=int(m.group(1));b=int(m.group(2)) if m.group(2) else n-1;code=206
            self.send_response(code);self.send_header('Content-Type','audio/mpeg');self.send_header('Accept-Ranges','bytes')
            self.send_header('Content-Length',str(b-a+1))
            if code==206:self.send_header('Content-Range','bytes %d-%d/%d'%(a,b,n))
            self.end_headers();self.wfile.write(d[a:b+1]);return
        return super().do_GET()
    def log_message(self,*a):pass
http.server.ThreadingHTTPServer(('',int(sys.argv[1])),H).serve_forever()
