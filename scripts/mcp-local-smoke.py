#!/usr/bin/env python3
"""Local-only OAuth/MCP smoke test. Requires local profile with mock auth enabled. Creates test catalog packages."""
import urllib.request,urllib.parse,urllib.error,json,http.cookiejar,secrets,hashlib,base64,re
base='http://localhost:8080'; jar=http.cookiejar.CookieJar()
class NoRedirect(urllib.request.HTTPRedirectHandler):
 def redirect_request(self,*args,**kwargs):return None
o=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar),NoRedirect())
def call(path,data=None,headers=None,method=None):
 req=urllib.request.Request(base+path if path.startswith('/') else path,data=data,headers=headers or {},method=method)
 try:r=o.open(req)
 except urllib.error.HTTPError as e:r=e
 raw=r.read();return r.status,dict(r.headers),raw

def form(path,params):return call(path,urllib.parse.urlencode(params,doseq=True).encode(),{'Content-Type':'application/x-www-form-urlencoded'})
status,h,b=call('/oauth2/register',json.dumps({'client_name':'MCP local smoke','redirect_uris':['http://127.0.0.1:12345/callback'],'scope':'resource:read skill:read','token_endpoint_auth_method':'client_secret_post'}).encode(),{'Content-Type':'application/json'})
assert status==201,(status,b);reg=json.loads(b)
status,h,b=call('/api/v1/auth/me',headers={'X-Mock-User-Id':'local-user'});assert status==200
v=secrets.token_urlsafe(48);challenge=base64.urlsafe_b64encode(hashlib.sha256(v.encode()).digest()).rstrip(b'=').decode()
params=dict(response_type='code',client_id=reg['client_id'],redirect_uri='http://127.0.0.1:12345/callback',scope='resource:read skill:read',resource=base+'/mcp',state='smoke',code_challenge=challenge,code_challenge_method='S256')
status,h,b=call('/oauth2/authorize?'+urllib.parse.urlencode(params));assert status==302,(status,b)
consent=urllib.parse.parse_qs(urllib.parse.urlparse(h['Location']).query)
status,h,b=call(h['Location']);assert status==200 and 'JoyHub'.encode() in b,(status,b[:200])
status,h,b=form('/oauth2/authorize',dict(client_id=reg['client_id'],state=consent['state'][0],scope=['resource:read','skill:read']));assert status==302,(status,b)
code=urllib.parse.parse_qs(urllib.parse.urlparse(h['Location']).query)['code'][0]
status,h,b=form('/oauth2/token',dict(grant_type='authorization_code',code=code,code_verifier=v,redirect_uri=params['redirect_uri'],resource=base+'/mcp',client_id=reg['client_id'],client_secret=reg['client_secret']))
assert status==200,(status,b[:1000]);tokens=json.loads(b);print('PASS OAuth authorization and token exchange')
headers={'Authorization':'Bearer '+tokens['access_token'],'Content-Type':'application/json','Accept':'application/json, text/event-stream'}
def rpc(method,params):
 status,h,b=call('/mcp',json.dumps(dict(jsonrpc='2.0',id=1,method=method,params=params)).encode(),headers)
 assert status==200,(status,b[:1000]);response=json.loads(b);assert 'error' not in response,response
 return response['result']
r=rpc('initialize',dict(protocolVersion='2025-11-25',capabilities={},clientInfo=dict(name='smoke',version='1.0')));print('PASS MCP initialize',r['protocolVersion'])
r=rpc('tools/list',{});print('PASS MCP tools',','.join(t['name'] for t in r['tools']))
r=rpc('tools/call',dict(name='search_resources',arguments=dict(query='')));assert not r.get('isError'),r;print('PASS shared resource search')
# Create a local-only catalog package through existing portal APIs.
import io,zipfile
csrf=next(cookie.value for cookie in jar if cookie.name=='XSRF-TOKEN')
slug='mcp-smoke-'+secrets.token_hex(4)
body=dict(slug=slug,name='MCP smoke package',summary='Resource distribution smoke test',kind='RESOURCE_PACK',documentation='# Smoke package',version='1.0.0',primaryDepartmentId=1,visibilityScope='COMPANY',publish=False)
status,h,b=call('/api/v1/catalog/resources',json.dumps(body).encode(),{'Content-Type':'application/json','X-XSRF-TOKEN':csrf});assert status==200,(status,b)
item=json.loads(b)['data'];rid='catalog:'+str(item['id'])
archive=io.BytesIO()
with zipfile.ZipFile(archive,'w') as z:z.writestr('README.md','MCP test package')
zipbytes=archive.getvalue();boundary=secrets.token_hex(16)
payload=('--'+boundary+'\r\nContent-Disposition: form-data; name="file"; filename="smoke.zip"\r\nContent-Type: application/zip\r\n\r\n').encode()+zipbytes+('\r\n--'+boundary+'--\r\n').encode()
status,h,b=call('/api/v1/catalog/resources/'+slug+'/artifact',payload,{'Content-Type':'multipart/form-data; boundary='+boundary,'X-XSRF-TOKEN':csrf});assert status==200,(status,b)
result=rpc('tools/call',dict(name='get_resource',arguments=dict(resourceId=rid)));assert not result.get('isError'),result
record=result['structuredContent'];assert record['downloadable'],record
result=rpc('tools/call',dict(name='check_updates',arguments=dict(installed=[dict(resourceId=rid,version='1.0.0',revision='old')])));assert result['structuredContent']['items'][0]['status']=='UPDATE_AVAILABLE',result
result=rpc('tools/call',dict(name='get_download',arguments=dict(resourceId=rid,revision=record['revision'])));assert not result.get('isError'),result
link=result['structuredContent']['downloadUrl']
status,h,b=call(link);assert status==200 and b==zipbytes,(status,b[:100]);assert call(link)[0]==404
print('PASS catalog detail, update check, exact ZIP download and ticket replay rejection')
# Revoke the grant after file transfer, then verify MCP stops accepting its access token.
status,h,b=form('/oauth2/revoke',dict(token=tokens['refresh_token'],token_type_hint='refresh_token',client_id=reg['client_id'],client_secret=reg['client_secret']));assert status==200
status,h,b=call('/mcp',json.dumps(dict(jsonrpc='2.0',id=2,method='tools/list',params={})).encode(),headers);assert status==401
print('PASS revoked OAuth grant rejected by MCP')
