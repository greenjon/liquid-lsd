// LD_PRELOAD: net bytes still held, per calling address. Writes LEAK_OUT every 2 minutes. Only for test runs.
#define _GNU_SOURCE
#include <dlfcn.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <unistd.h>
extern void *__libc_malloc(size_t); extern void __libc_free(void*); extern void *__libc_calloc(size_t,size_t); extern void *__libc_realloc(void*,size_t);
#define MAGIC 0x5AFEC0DE1EAC5AFEULL
#define N (1<<16)
typedef struct { _Atomic uintptr_t addr; _Atomic int64_t bytes, count, total; } Site;
static Site sites[N];
typedef struct { uint64_t magic; uint32_t site; uint32_t pad; size_t size; size_t pad2; } Hdr; // 32 bytes keeps 16-byte alignment
static uint32_t site_of(uintptr_t a){ uint32_t i=(uint32_t)((a*0x9E3779B97F4A7C15ULL)>>48);
  for(;;i=(i+1)&(N-1)){ uintptr_t c=atomic_load_explicit(&sites[i].addr,memory_order_relaxed); if(c==a) return i; if(c==0){ uintptr_t z=0; if(atomic_compare_exchange_strong(&sites[i].addr,&z,a)||z==a) return i; } } }
static void *wrap(void *p,size_t s,uintptr_t ra){ if(!p) return 0; Hdr*h=p; uint32_t i=site_of(ra); h->magic=MAGIC;h->site=i;h->size=s; atomic_fetch_add_explicit(&sites[i].bytes,s,memory_order_relaxed); atomic_fetch_add_explicit(&sites[i].count,1,memory_order_relaxed); atomic_fetch_add_explicit(&sites[i].total,s,memory_order_relaxed); return h+1; }
static inline Hdr *hdr(void*p){ Hdr*h=((Hdr*)p)-1; return h->magic==MAGIC?h:0; }
static void unrec(Hdr*h){ atomic_fetch_sub_explicit(&sites[h->site].bytes,h->size,memory_order_relaxed); atomic_fetch_sub_explicit(&sites[h->site].count,1,memory_order_relaxed); h->magic=0; }
void *malloc(size_t s){ return wrap(__libc_malloc(s+sizeof(Hdr)),s,(uintptr_t)__builtin_return_address(0)); }
void *calloc(size_t a,size_t b){ size_t s=a*b; return wrap(__libc_calloc(1,s+sizeof(Hdr)),s,(uintptr_t)__builtin_return_address(0)); }
void free(void*p){ if(!p) return; Hdr*h=hdr(p); if(h){ unrec(h); __libc_free(h);} else __libc_free(p); }
void *realloc(void*p,size_t s){ uintptr_t ra=(uintptr_t)__builtin_return_address(0); if(!p) return wrap(__libc_malloc(s+sizeof(Hdr)),s,ra); Hdr*h=hdr(p); if(!h) return __libc_realloc(p,s); if(s==0){ unrec(h); __libc_free(h); return 0; } unrec(h); return wrap(__libc_realloc(h,s+sizeof(Hdr)),s,ra); }
size_t malloc_usable_size(void*p){ extern size_t __malloc_usable_size(void*) __attribute__((weak)); if(!p) return 0; Hdr*h=hdr(p); if(h) return h->size; return 0; }
typedef struct {uintptr_t a; int64_t b,c,t;} Row;
static int cmp(const void*x,const void*y){ int64_t d=((Row*)y)->b-((Row*)x)->b; return d>0?1:d<0?-1:0; }
static void *dump(void*_){ const char*out=getenv("LEAK_OUT"); static Row r[N]; int minute=0;
  for(;;){ sleep(120); minute+=2; int n=0; for(int i=0;i<N;i++){ uintptr_t a=atomic_load(&sites[i].addr); if(a){ r[n++]=(Row){a,atomic_load(&sites[i].bytes),atomic_load(&sites[i].count),atomic_load(&sites[i].total)}; } }
    qsort(r,n,sizeof(Row),cmp); FILE*f=fopen(out,"a"); if(!f) continue; fprintf(f,"== %d min\n",minute);
    for(int i=0;i<n&&i<25;i++){ Dl_info d; memset(&d,0,sizeof d); dladdr((void*)r[i].a,&d); const char*b=d.dli_fname?strrchr(d.dli_fname,'/'):0; fprintf(f,"%10ld KB %9ld live %s+0x%lx %s\n",(long)(r[i].b/1024),(long)r[i].c,b?b+1:"?",(unsigned long)(r[i].a-(uintptr_t)d.dli_fbase),d.dli_sname?d.dli_sname:""); }
    fclose(f); } return 0; }
__attribute__((constructor)) static void init(void){ if(!getenv("LEAK_OUT")) return; char c[32]={0}; FILE*f=fopen("/proc/self/comm","r"); if(f){ fgets(c,31,f); fclose(f);} if(strncmp(c,"java",4)) return; pthread_t t; pthread_create(&t,0,dump,0); pthread_detach(t); }
