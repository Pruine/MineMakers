#!/usr/bin/env python3
"""Reproduce the cloud development tools, build, and protocol/adapter checks."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile
from urllib.parse import urlparse

ROOT=Path(__file__).resolve().parents[1]
TOOLS=Path('/workspace/.tools')
TOOLS.mkdir(exist_ok=True)
env=os.environ.copy()
candidates=[Path(env['JAVA_HOME'])] if env.get('JAVA_HOME') else []
candidates += sorted(TOOLS.glob('jdk-*'),reverse=True)
java_home=next((p for p in candidates if (p/'bin/javac').is_file()),None)
if java_home is None:
    archive=TOOLS/'minemakers-jdk21.tar.gz'
    checksum=TOOLS/'minemakers-jdk21.sha256'
    url='https://download.oracle.com/java/21/latest/jdk-21_linux-x64_bin.tar.gz'
    for source,destination in [(url,archive),(url+'.sha256',checksum)]:
        subprocess.run(['curl','--fail','--location','--silent','--show-error',source,'-o',str(destination)],check=True)
    actual=hashlib.sha256(archive.read_bytes()).hexdigest()
    if actual!=checksum.read_text().strip().split()[0]: raise SystemExit('JDK checksum mismatch')
    with tarfile.open(archive) as tar:
        roots={p.name.split('/')[0] for p in tar.getmembers() if p.name}
        if len(roots)!=1: raise SystemExit('Unexpected JDK archive layout')
        java_home=TOOLS/next(iter(roots))
        if java_home.exists(): raise SystemExit('Refusing to overwrite an existing JDK directory')
        tar.extractall(TOOLS,filter='data')
env['JAVA_HOME']=str(java_home)
env['GRADLE_USER_HOME']=str(TOOLS/'gradle')
# Use the environment's managed CA trust, never disable certificate validation.
opts=env.get('JAVA_TOOL_OPTIONS','')
trust=Path('/etc/ssl/certs/java/cacerts')
if trust.is_file(): opts+=' -Djavax.net.ssl.trustStore='+str(trust)
proxy=urlparse(env.get('HTTPS_PROXY',env.get('https_proxy','')))
if proxy.hostname:
    if proxy.username or proxy.password: raise SystemExit('Use the platform-managed proxy authentication; do not copy credentials into JVM arguments')
    opts+=f' -Dhttps.proxyHost={proxy.hostname} -Dhttps.proxyPort={proxy.port or 80} -Dhttp.proxyHost={proxy.hostname} -Dhttp.proxyPort={proxy.port or 80}'
env['JAVA_TOOL_OPTIONS']=opts.strip()
subprocess.run(['./gradlew','--no-daemon','build'],cwd=ROOT/'fabric',env=env,check=True)
deps=TOOLS/'minemakers-test-deps'
subprocess.run([sys.executable,'-m','pip','install','--target',str(deps),'-r',str(ROOT/'tools/test-requirements.txt')],check=True)
env['PYTHONPATH']=str(deps)+(os.pathsep+env['PYTHONPATH'] if env.get('PYTHONPATH') else '')
subprocess.run([sys.executable,str(ROOT/'tools/test.py')],cwd=ROOT,env=env,check=True)
