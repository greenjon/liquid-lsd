import sys
from PIL import Image,ImageDraw
d,out,names=sys.argv[1],sys.argv[2],sys.argv[3:]
c=3;w,h=640,360
r=(len(names)+c-1)//c
im=Image.new('RGB',(c*w,r*(h+14)),(0,0,0));D=ImageDraw.Draw(im)
for i,n in enumerate(names):
    x,y=(i%c)*w,(i//c)*(h+14)
    im.paste(Image.open(f'{d}/{n}.png').convert('RGB').resize((w,h),Image.LANCZOS),(x,y+14));D.text((x+2,y+1),n,fill=(255,255,0))
im.save(out)
