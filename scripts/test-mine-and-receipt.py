import urllib.request, urllib.parse, json

url = 'http://localhost:8180/realms/swiftdrop/protocol/openid-connect/token'
data = urllib.parse.urlencode({
    'client_id': 'swiftdrop-test',
    'grant_type': 'password',
    'username': 'customer@swiftdrop.lk',
    'password': 'Swift@2026'
}).encode()

req = urllib.request.Request(url, data=data)
with urllib.request.urlopen(req) as resp:
    token = json.loads(resp.read().decode())['access_token']

gw_req = urllib.request.Request('http://localhost:8080/api/v1/parcels/mine')
gw_req.add_header('Authorization', f'Bearer {token}')
with urllib.request.urlopen(gw_req) as gw_resp:
    res = json.loads(gw_resp.read().decode())
    print('Total parcels found:', res['page']['totalElements'])
    first_id = None
    for p in res['content']:
        print(f"[{p['status']}] {p['trackingNumber']} - {p['pickup']['city']} -> {p['dropoff']['city']} (Fee: {p['fee']['currency']} {p['fee']['amount']})")
        if first_id is None:
            first_id = p['id']

if first_id:
    print(f"\nTesting receipt download for parcel {first_id}...")
    rcpt_req = urllib.request.Request(f'http://localhost:8080/api/v1/parcels/{first_id}/receipt')
    rcpt_req.add_header('Authorization', f'Bearer {token}')
    with urllib.request.urlopen(rcpt_req) as rcpt_resp:
        content_type = rcpt_resp.headers.get('Content-Type')
        content_disp = rcpt_resp.headers.get('Content-Disposition')
        pdf_bytes = rcpt_resp.read()
        print('Receipt HTTP status:', rcpt_resp.status)
        print('Content-Type:', content_type)
        print('Content-Disposition:', content_disp)
        print('PDF bytes length:', len(pdf_bytes))
        print('Header:', pdf_bytes[:8])
        if pdf_bytes.startswith(b'%PDF-'):
            print('SUCCESS: Valid OpenPDF receipt received!')
        else:
            print('FAILURE: Not a valid PDF!')
