#!/usr/bin/env python3
"""oscsend.py <marks file>: OSC traffic of every kind into 127.0.0.1:8000, with a time mark per step (for the broadcast check)."""
import socket,struct,time,sys,random
M=open(sys.argv[1],'w'); s=socket.socket(socket.AF_INET,socket.SOCK_DGRAM); dst=('127.0.0.1',8000)
def st(x): b=x.encode()+b'\0'; return b+b'\0'*(-len(b)%4)
def msg(a,t,payload=b''): return st(a)+st(t)+payload
def mark(n): M.write(f'{time.time():.2f} {n}\n'); M.flush()
def send(p,n): s.sendto(p,dst); mark(n)
time.sleep(25)
send(msg('/t/xf',',f',struct.pack('>f',.1)),'f 0.1'); time.sleep(4)
send(msg('/t/xf',',d',struct.pack('>d',.9)),'d 0.9'); time.sleep(4)
random.seed(1)
for i in range(200): s.sendto(bytes(random.randrange(256) for _ in range(random.randrange(1,300))),dst)
s.sendto(b'/t/xf\0\0\0,f\0\0',dst); s.sendto(msg('/t/xf',',qf',struct.pack('>if',7,.05)),dst)
s.sendto(b'#bundle\0'+b'\0'*8+struct.pack('>i',9999),dst); mark('garbage'); time.sleep(4)
inner=msg('/t/xf',',f',struct.pack('>f',.3)); send(b'#bundle\0'+b'\0'*8+struct.pack('>i',len(inner))+inner,'bundle 0.3'); time.sleep(4)
send(msg('/t/xy',',ff',struct.pack('>ff',.2,.6)),'xy y 0.6'); time.sleep(4)
send(msg('/t/xf',',h',struct.pack('>q',1)),'h 1'); time.sleep(4)
t0=time.time()
for i in range(20000): s.sendto(msg('/t/xf',',f',struct.pack('>f',(i%100)/100)),dst)
mark(f'flood 20000 in {time.time()-t0:.2f}s'); time.sleep(3)
send(msg('/t/xf',',f',struct.pack('>f',.5)),'f 0.5 after flood'); time.sleep(5)
mark('end')
