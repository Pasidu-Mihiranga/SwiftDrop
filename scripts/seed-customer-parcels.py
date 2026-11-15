import os, base64, uuid, json, subprocess
from datetime import datetime, timezone, timedelta
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

# Read PII_ENCRYPTION_KEY and DB password from .env
env = {}
with open('.env') as f:
    for line in f:
        line = line.strip()
        if line and not line.startswith('#') and '=' in line:
            k, v = line.split('=', 1)
            env[k] = v

pii_key_raw = base64.b64decode(env['PII_ENCRYPTION_KEY'])
aesgcm = AESGCM(pii_key_raw)

ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
def check_char(payload):
    base = len(ALPHABET)
    factor = 2
    total = 0
    for i in range(len(payload) - 1, -1, -1):
        addend = factor * ALPHABET.index(payload[i])
        factor = 1 if factor == 2 else 2
        total += addend // base + addend % base
    return ALPHABET[(base - total % base) % base]

def make_tracking(date_str, body):
    payload = date_str + body
    return "SD" + payload + check_char(payload)

def enc(text):
    if text is None:
        return None
    iv = os.urandom(12)
    ct = aesgcm.encrypt(iv, text.encode('utf-8'), None)
    return 'v1:' + base64.b64encode(iv + ct).decode('ascii')

customer_id = '10000000-0000-4000-8000-000000000005'
now = datetime.now(timezone.utc)

parcels = [
    {
        'id': str(uuid.uuid4()),
        'tracking_number': make_tracking('261113', 'K7Q2M9'),
        'source': 'CUSTOMER',
        'customer_id': customer_id,
        'pickup_json': json.dumps({'line1': '14/2 Galle Road', 'city': 'Colombo 03', 'district': 'Colombo', 'postalCode': '00300'}),
        'dropoff_json': json.dumps({'line1': '78 Peradeniya Road', 'city': 'Kandy', 'district': 'Kandy', 'postalCode': '20000'}),
        'pickup_zone': 'COLOMBO_METRO',
        'dropoff_zone': 'KANDY_CENTRAL',
        'recipient_name': 'Saman Perera',
        'recipient_phone': '0771234567',
        'recipient_email': 'saman@example.lk',
        'weight_kg': 2.50,
        'size_class': 'M',
        'service_level': 'EXPRESS',
        'fee': 850.00,
        'currency': 'LKR',
        'status': 'DELIVERED',
        'courier_id': 'courier-001',
        'created_at': now - timedelta(days=2),
        'updated_at': now - timedelta(hours=4),
        'history': [
            (None, 'CREATED', customer_id, 'CUSTOMER', 'Booking confirmed via web portal', now - timedelta(days=2)),
            ('CREATED', 'PICKED_UP', 'courier-001', 'COURIER', 'Parcel collected from sender', now - timedelta(days=1, hours=20)),
            ('PICKED_UP', 'IN_TRANSIT', 'courier-001', 'COURIER', 'Sorted at Colombo Sorting Center', now - timedelta(days=1, hours=10)),
            ('IN_TRANSIT', 'OUT_FOR_DELIVERY', 'courier-001', 'COURIER', 'Out with courier for final delivery', now - timedelta(hours=6)),
            ('OUT_FOR_DELIVERY', 'DELIVERED', 'courier-001', 'COURIER', 'Successfully handed over to recipient Saman Perera', now - timedelta(hours=4)),
        ]
    },
    {
        'id': str(uuid.uuid4()),
        'tracking_number': make_tracking('261114', 'H3V8P2'),
        'source': 'CUSTOMER',
        'customer_id': customer_id,
        'pickup_json': json.dumps({'line1': '55 Havelock Road', 'city': 'Colombo 05', 'district': 'Colombo', 'postalCode': '00500'}),
        'dropoff_json': json.dumps({'line1': '120 Main Street', 'city': 'Galle', 'district': 'Galle', 'postalCode': '80000'}),
        'pickup_zone': 'COLOMBO_METRO',
        'dropoff_zone': 'GALLE_MUNICIPAL',
        'recipient_name': 'Anura Jayasinghe',
        'recipient_phone': '0719876543',
        'recipient_email': 'anura@example.lk',
        'weight_kg': 1.20,
        'size_class': 'S',
        'service_level': 'STANDARD',
        'fee': 450.00,
        'currency': 'LKR',
        'status': 'IN_TRANSIT',
        'courier_id': 'courier-002',
        'created_at': now - timedelta(days=1),
        'updated_at': now - timedelta(hours=2),
        'history': [
            (None, 'CREATED', customer_id, 'CUSTOMER', 'Booking confirmed', now - timedelta(days=1)),
            ('CREATED', 'PICKED_UP', 'courier-002', 'COURIER', 'Collected from sender', now - timedelta(hours=8)),
            ('PICKED_UP', 'IN_TRANSIT', 'courier-002', 'COURIER', 'In transit to Southern Hub', now - timedelta(hours=2)),
        ]
    },
    {
        'id': str(uuid.uuid4()),
        'tracking_number': make_tracking('261115', 'W5N9K4'),
        'source': 'CUSTOMER',
        'customer_id': customer_id,
        'pickup_json': json.dumps({'line1': '22 Union Place', 'city': 'Colombo 02', 'district': 'Colombo', 'postalCode': '00200'}),
        'dropoff_json': json.dumps({'line1': '94 Kandy Road', 'city': 'Kurunegala', 'district': 'Kurunegala', 'postalCode': '60000'}),
        'pickup_zone': 'COLOMBO_METRO',
        'dropoff_zone': 'NORTH_WESTERN',
        'recipient_name': 'Chathuri Bandara',
        'recipient_phone': '0765554321',
        'recipient_email': 'chathuri@example.lk',
        'weight_kg': 0.80,
        'size_class': 'S',
        'service_level': 'EXPRESS',
        'fee': 1200.00,
        'currency': 'LKR',
        'status': 'CREATED',
        'courier_id': None,
        'created_at': now - timedelta(hours=1),
        'updated_at': now - timedelta(hours=1),
        'history': [
            (None, 'CREATED', customer_id, 'CUSTOMER', 'Booking confirmed - awaiting pickup assignment', now - timedelta(hours=1)),
        ]
    }
]

sql = []
for p in parcels:
    p_enc = enc(p['pickup_json'])
    d_enc = enc(p['dropoff_json'])
    ph_enc = enc(p['recipient_phone'])
    em_enc = enc(p['recipient_email'])
    c_val = f"'{p['courier_id']}'" if p['courier_id'] else "NULL"
    
    sql.append(f"""
    INSERT INTO parcel (id, tracking_number, source, customer_id, pickup_address_enc, dropoff_address_enc,
                        pickup_zone, dropoff_zone, recipient_name, recipient_phone_enc, recipient_email_enc,
                        weight_kg, size_class, service_level, fee, currency, status, courier_id, created_at, updated_at, version)
    VALUES ('{p['id']}', '{p['tracking_number']}', '{p['source']}', '{p['customer_id']}', '{p_enc}', '{d_enc}',
            '{p['pickup_zone']}', '{p['dropoff_zone']}', '{p['recipient_name']}', '{ph_enc}', '{em_enc}',
            {p['weight_kg']}, '{p['size_class']}', '{p['service_level']}', {p['fee']}, '{p['currency']}',
            '{p['status']}', {c_val}, '{p['created_at'].isoformat()}', '{p['updated_at'].isoformat()}', 0)
    ON CONFLICT (tracking_number) DO NOTHING;
    """)
    for h in p['history']:
        hid = str(uuid.uuid4())
        from_st = f"'{h[0]}'" if h[0] else "NULL"
        sql.append(f"""
        INSERT INTO parcel_status_history (id, parcel_id, from_status, to_status, actor_id, actor_role, note, occurred_at)
        VALUES ('{hid}', '{p['id']}', {from_st}, '{h[1]}', '{h[2]}', '{h[3]}', '{h[4]}', '{h[5].isoformat()}');
        """)

sql_text = '\n'.join(sql)
res = subprocess.run(['docker', 'exec', '-i', 'swiftdrop-postgres-1', 'psql', '-U', 'swiftdrop_admin', '-d', 'parcels_db'],
                     input=sql_text, text=True, capture_output=True)
print('SQL exit code:', res.returncode)
if res.stdout:
    print('stdout:', res.stdout[:200])
if res.stderr:
    print('stderr:', res.stderr[:200])
print('Done seeding sample customer parcels!')
