"""Rebuild the courier operations design PDF and editable vector diagrams. Requires reportlab."""
from pathlib import Path
from html import escape
import math
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.utils import simpleSplit

ROOT = Path(__file__).resolve().parents[4]
OUT = ROOT / 'output/pdf/m4-courier-status-design.pdf'
FIG = Path(__file__).resolve().parents[1]
OUT.parent.mkdir(parents=True, exist_ok=True)
W,H = landscape(A4)
c = canvas.Canvas(str(OUT), pagesize=(W,H))
c.setTitle('SwiftDrop - Courier Operations and Status Lifecycle - Pamindu Piyumaka')
c.setAuthor('Pamindu Piyumaka')
page_no = 0

def txt(x,y,s,size=10,bold=False,color='#17191c',align='left'):
    c.setFillColor(color); c.setFont('Helvetica-Bold' if bold else 'Helvetica',size)
    getattr(c,{'left':'drawString','center':'drawCentredString','right':'drawRightString'}[align])(x,H-y,s)

def para(s,y,width=750,size=11):
    for line in simpleSplit(s,'Helvetica',size,width):
        txt(42,y,line,size); y+=size*1.5
    return y

def start(title,sub=''):
    global page_no
    page_no+=1
    txt(42,29,'SWIFTDROP  /  COURIER OPERATIONS',8,bold=True,color='#606773')
    txt(42,66,title,21,True)
    if sub: para(sub,91,size=10.5)
    c.setStrokeColorRGB(.83,.85,.88); c.line(42,40,W-42,40)
    txt(W-42,H-22,f'Pamindu Piyumaka  |  {page_no}',8,color='#606773',align='right')

def end(): c.showPage()

class Diagram:
    def __init__(self,name,title,y=132,height=365):
        self.name=name; self.parts=[]; self.scale=(W-92)/1000; self.y=y
        txt(48,y-12,title,11,True)
        c.saveState(); c.translate(46,H-y); c.scale(self.scale,-self.scale)
    def line(self,pts,dash=False,arrow=False):
        c.setStrokeColorRGB(.12,.12,.12); c.setLineWidth(1)
        c.setDash([4,3] if dash else [])
        p=c.beginPath(); p.moveTo(*pts[0])
        for q in pts[1:]: p.lineTo(*q)
        c.drawPath(p)
        self.parts.append('<polyline points="'+ ' '.join(f'{x},{y}' for x,y in pts)+'" fill="none" stroke="#222"'+(' stroke-dasharray="4 3"' if dash else '')+'/>')
        if arrow:
            x,y=pts[-1]; a,b=pts[-2]; t=math.atan2(y-b,x-a)
            arr=[(x-8*math.cos(t-.45),y-8*math.sin(t-.45)),(x,y),(x-8*math.cos(t+.45),y-8*math.sin(t+.45))]
            self.line(arr)
        c.setDash([])
    def text(self,x,y,s,size=13,bold=False):
        lines=s.split('|')
        for i,line in enumerate(lines):
            yy=y+i*size*1.32
            c.saveState(); c.translate(x,yy); c.scale(1,-1)
            c.setFillColorRGB(.08,.08,.08); c.setFont('Helvetica-Bold' if bold else 'Helvetica',size); c.drawCentredString(0,0,line); c.restoreState()
            self.parts.append(f'<text x="{x}" y="{yy}" text-anchor="middle" font-family="Arial, sans-serif" font-size="{size}"'+(' font-weight="bold"' if bold else '')+f'>{escape(line)}</text>')
    def box(self,x,y,w,h,s,size=13,ellipse=False):
        c.setFillColorRGB(1,1,1); c.setStrokeColorRGB(.12,.12,.12); c.setLineWidth(1)
        if ellipse:
            c.ellipse(x,y,x+w,y+h,fill=1,stroke=1)
            self.parts.append(f'<ellipse cx="{x+w/2}" cy="{y+h/2}" rx="{w/2}" ry="{h/2}" fill="white" stroke="#222"/>')
        else:
            c.roundRect(x,y,w,h,6,fill=1,stroke=1)
            self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="6" fill="white" stroke="#222"/>')
        self.text(x+w/2,y+h/2-(len(s.split('|'))-1)*size*.66+size*.34,s,size)
    def actor(self,x,y,label):
        c.setFillColorRGB(1,1,1); c.circle(x,y,8,fill=1)
        self.parts.append(f'<circle cx="{x}" cy="{y}" r="8" fill="white" stroke="#222"/>')
        self.line([(x,y+8),(x,y+36)]); self.line([(x-15,y+19),(x+15,y+19)]); self.line([(x-13,y+52),(x,y+36),(x+13,y+52)])
        self.text(x,y+73,label,12)
    def entity(self,x,y,w,name,fields):
        h=50+len(fields)*18
        self.box(x,y,w,h,'',12)
        self.text(x+w/2,y+24,name,14,True)
        self.line([(x,y+36),(x+w,y+36)])
        for i,field in enumerate(fields):
            self.text(x+w/2,y+54+i*18,field,12)
        return h
    def decision(self,x,y,w,h,label):
        pts=[(x,y-h/2),(x+w/2,y),(x,y+h/2),(x-w/2,y),(x,y-h/2)]
        c.setFillColorRGB(1,1,1); c.setStrokeColorRGB(.12,.12,.12)
        p=c.beginPath(); p.moveTo(*pts[0])
        for q in pts[1:]: p.lineTo(*q)
        c.drawPath(p,fill=1,stroke=1)
        self.parts.append('<polygon points="'+' '.join(f'{a},{b}' for a,b in pts)+'" fill="white" stroke="#222"/>')
        if label: self.text(x,y+4,label,11)
    def node(self,x,y,final=False):
        c.setStrokeColorRGB(.12,.12,.12); c.setFillColorRGB(.12,.12,.12)
        if final:
            c.circle(x,y,10,fill=0,stroke=1)
            self.parts.append(f'<circle cx="{x}" cy="{y}" r="10" fill="white" stroke="#222"/>')
        c.circle(x,y,6,fill=1,stroke=0)
        self.parts.append(f'<circle cx="{x}" cy="{y}" r="6" fill="#222"/>')
    def realizes(self,pts):
        self.line(pts,dash=True)
        x,y=pts[-1]; a,b=pts[-2]; t=math.atan2(y-b,x-a)
        tri=[(x,y),(x-14*math.cos(t-.40),y-14*math.sin(t-.40)),(x-14*math.cos(t+.40),y-14*math.sin(t+.40))]
        c.setFillColorRGB(1,1,1); c.setStrokeColorRGB(.12,.12,.12)
        p=c.beginPath(); p.moveTo(*tri[0])
        for q in tri[1:]: p.lineTo(*q)
        p.close(); c.drawPath(p,fill=1,stroke=1)
        self.parts.append('<polygon points="'+' '.join(f'{a},{b}' for a,b in tri)+'" fill="white" stroke="#222"/>')
    def note(self,x,y,w,s): self.box(x,y,w,45,s,11)
    def label(self,x,y,s,width,size=10):
        lines=simpleSplit(s,'Helvetica',size,width)
        yy=y-(len(lines)-1)*12
        w=max(c.stringWidth(z,'Helvetica',size) for z in lines)+8
        h=len(lines)*12+3
        c.setFillColorRGB(1,1,1); c.rect(x-w/2,yy-10,w,h,fill=1,stroke=0)
        self.parts.append(f'<rect x="{x-w/2}" y="{yy-10}" width="{w}" height="{h}" fill="white"/>')
        self.text(x,yy,'|'.join(lines),size)
    def finish(self):
        c.restoreState()
        (FIG/(self.name+'.svg')).write_text('<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="480" viewBox="0 0 1000 480"><rect width="1000" height="480" fill="white"/>'+''.join(self.parts)+'</svg>',encoding='utf-8')

def sequence(name,title,lanes,steps,notes):
    start(title,notes)
    d=Diagram(name,'Sequence diagram',y=143)
    xs=[50+i*900/(len(lanes)-1) for i in range(len(lanes))]
    for x,label in zip(xs,lanes):
        d.box(x-49,0,98,42,label,11)
        d.line([(x,42),(x,460)],dash=True)
    for n,(a,b,label,kind) in enumerate(steps):
        y=78+n*33
        if a==b:
            d.line([(xs[a],y),(xs[a]+35,y),(xs[a]+35,y+15),(xs[a],y+15)],arrow=True)
            d.label(xs[a]-110 if xs[a]>750 else xs[a]+130,y+3,label,190,10)
        else:
            d.line([(xs[a],y),(xs[b],y)],dash=kind=='return',arrow=True)
            d.label((xs[a]+xs[b])/2,y-7,label,abs(xs[b]-xs[a])-12,10)
    d.finish(); end()

start('Courier Operations and Status Lifecycle')
txt(42,113,'Design documentation  /  Pamindu Piyumaka',14,True)
para('Courier operations for SwiftDrop: assigned parcels, controlled status changes, proof of delivery and failed-delivery handling. Every accepted change records history and an event in one database transaction.',153,width=675,size=13)
txt(42,237,'SERVICE  parcel-service (status module)     WEB  apps/web /courier',10,True)
txt(42,259,'DATA  PostgreSQL parcels_db  /  RustFS (S3)     INTEGRATION  Kafka',10,True)
for y,s in zip(range(308,449,28),['5.1  Courier use cases','5.2  Parcel state machine','5.3  Sequences and workflow activity diagrams','5.4  Data model, architecture and integration design','5.5  Design patterns and principles']): txt(42,y,s,13)
end()

start('5.1  Courier use cases','Primary actor: the assigned courier. Dispatcher/Admin handles re-attempts. Customer cancellation belongs to Customer Booking.')
d=Diagram('01-courier-use-cases','Figure 1. Courier operations within SwiftDrop')
d.line([(210,0),(790,0),(790,450),(210,450),(210,0)])
d.text(500,22,'Courier operations and parcel status lifecycle',13,True)
d.actor(80,155,'Assigned courier'); d.actor(910,325,'Dispatcher / Admin')
for x,y,w,h,s in [(250,45,240,50,"View today's run sheet"),(250,125,240,50,'Read assigned parcel'),(250,205,240,50,'Update parcel status'),(540,205,220,50,'Check access and transition'),(250,300,240,50,'Record delivery proof'),(540,300,220,50,'Mark delivery failed'),(540,385,220,50,'Authorize re-attempt')]: d.box(x,y,w,h,s,13,True)
for y in [70,150,230,325]: d.line([(95,175),(175,175),(175,y),(250,y)])
d.line([(490,230),(540,230)],dash=True,arrow=True); d.text(515,195,'<<include>>',10)
d.line([(370,300),(370,255)],dash=True,arrow=True); d.text(310,282,'<<extend>>',10)
d.line([(650,300),(650,270),(450,270),(450,248)],dash=True,arrow=True); d.text(710,285,'<<extend>>',10)
d.line([(895,345),(815,345),(815,410),(760,410)])
d.text(370,372,'[target = DELIVERED]',11); d.text(650,365,'[target = FAILED]',11)
d.finish()
end()

start('5.2  Parcel state machine','Only the transitions drawn below are accepted. DELIVERED and CANCELLED are terminal. FAILED permits a staff-authorized re-attempt.')
d=Diagram('02-parcel-state','Figure 2. Status lifecycle and transition guards')
states=[(30,105,'CREATED'),(260,105,'PICKED_UP'),(490,105,'IN_TRANSIT'),(740,105,'OUT_FOR_DELIVERY'),(740,260,'DELIVERED'),(490,260,'FAILED'),(30,260,'CANCELLED')]
for x,y,s in states: d.box(x,y,190,55,s,14)
d.node(10,132); d.line([(16,132),(30,132)],arrow=True)
for a,b in [(220,260),(450,490),(680,740)]: d.line([(a,132),(b,132)],arrow=True)
d.text(125,75,'Customer Booking creates the parcel',11)
d.line([(125,160),(125,260)],arrow=True); d.text(210,214,'cancel (booking)',11)
d.line([(835,160),(835,260)],arrow=True); d.text(913,210,'[valid proof]',11)
d.line([(785,160),(785,210),(585,210),(585,260)],arrow=True); d.text(600,197,'[failure reason supplied]',11)
d.line([(585,315),(585,363),(960,363),(960,132),(930,132)],arrow=True); d.text(765,386,'re-attempt: DISPATCHER / ADMIN only',12)
d.line([(125,315),(125,330)],arrow=True); d.node(125,340,True)
d.line([(835,315),(835,330)],arrow=True); d.node(835,340,True)
d.note(30,405,430,'DELIVERED: recipient name and/or verified photo.|FAILED: one failure-reason enum value is required.')
d.note(490,405,470,'All other pairs are rejected, including self-transitions.|CREATED -> DELIVERED must never skip intermediate states.')
d.finish(); end()

sequence('03-status-update-sequence','5.3  Courier status update',
 ['Courier /|mobile BFF','Gateway','Status API','Policy +|State','parcels_db','Outbox relay','Kafka'],
 [(0,1,'PATCH status: note, proof, reason',''),(1,2,'Validated JWT + correlation ID',''),(2,3,'Role, owner, state and proof/reason checks',''),(3,2,'Valid command','return'),(2,4,'BEGIN: lock/version-check parcel; update status',''),(2,4,'Insert history + proof reference + outbox event',''),(4,2,'COMMIT all writes together','return'),(2,0,'200: confirmed status and allowed next steps','return'),(5,4,'Read unpublished outbox rows',''),(4,5,'ParcelStatusChanged envelope','return'),(5,6,'Publish status event; key = parcelId',''),(5,4,'Mark published after broker ACK','')],
 'Failure paths return before commit: customer role -> 403; foreign courier parcel -> 404; invalid transition or missing proof/reason -> clear validation error. Row locking or version checks prevent concurrent state skips.')

sequence('04-event-consumers-sequence','5.3  Status change to tracking and notifications',
 ['Kafka','Tracking','Notifications','Webhooks','Reporting','Recipient /|browser','Merchant'],
 [(0,1,'ParcelStatusChanged',''),(1,5,'Update timeline; Socket.IO status push',''),(0,2,'Same committed event',''),(2,5,'Preference-aware email / mock SMS',''),(0,3,'Same committed event',''),(3,6,'Queued, HMAC-signed webhook POST',''),(6,3,'HTTP response / timeout','return'),(3,3,'Retry with backoff; DLQ on exhaustion',''),(0,4,'Same committed event',''),(4,4,'Update delivery / courier statistics','')],
 'Consumers deduplicate by eventId and commit their own data before acknowledging processing. Fan-out is asynchronous: notification or webhook delays do not roll back an accepted courier update. Steps show independent reactions, not a required cross-service order.')

sequence('05-proof-upload-sequence','5.3  Private photo proof of delivery',
 ['Courier /|browser','BFF +|Gateway','Status API','S3ProofStorage','RustFS S3','parcels_db'],
 [(0,1,'POST proof-upload-url',''),(1,2,'Forward authenticated request',''),(2,3,'Check access, image type, size',''),(3,2,'Random key + presigned PUT','return'),(2,0,'Upload URL + proof reference','return'),(0,4,'Direct PUT image bytes; show progress',''),(4,0,'Upload complete','return'),(0,2,'PATCH status DELIVERED + proof reference / name',''),(2,4,'Verify object key, actual size and image type',''),(2,5,'TX: proof metadata + parcel + history + outbox',''),(2,0,'Confirmed delivery','return'),(2,0,'Authorized viewer: short-lived presigned GET','return')],
 'On startup, the S3 adapter creates swiftdrop-proofs if missing using S3_ENDPOINT. Keep the bucket private. The status command validates the uploaded object against the parcel and upload constraints. Uploaded bytes and the database cannot share one transaction; clean up abandoned uploads.')

start('5.3  Courier workflow activity diagram','The courier selects an assigned parcel and a valid next status. Decisions route delivery proof, failure details and normal progress through the same server-validated command.')
d=Diagram('11-courier-workflow-activity','Figure 6. Prepare, submit and confirm a courier status update')
d.node(500,12)
d.box(350,35,300,40,"Open run sheet; choose parcel|and a valid next status",12)
d.decision(500,125,150,50,'Target status?')
d.box(40,175,260,50,'Normal progress:|add an optional delivery note',12)
d.box(370,175,260,50,'DELIVERED: recipient name|and/or verified photo upload',12)
d.box(700,175,260,50,'FAILED: choose failure reason|and add an optional note',12)
d.decision(500,265,16,16,'')
d.box(350,290,300,40,'Submit status command|with note, proof or failure reason',12)
d.decision(500,375,150,50,'Accepted?')
d.box(350,425,300,35,'Confirm status; refresh run sheet',12)
d.box(720,350,260,50,'Show error; correct details|or refresh stale parcel; retry',12)
d.node(500,475,True)
for pts in [[(500,18),(500,35)],[(500,75),(500,100)],[(500,150),(500,175)],[(500,225),(500,257)],[(500,273),(500,290)],[(500,330),(500,350)],[(500,400),(500,425)],[(500,460),(500,465)]]: d.line(pts,arrow=True)
d.line([(425,125),(170,125),(170,175)],arrow=True); d.label(170,115,'[other valid step]',200,11)
d.label(565,165,'[DELIVERED]',120,10)
d.line([(575,125),(830,125),(830,175)],arrow=True); d.label(830,115,'[FAILED]',150,11)
d.line([(170,225),(170,265),(492,265)],arrow=True)
d.line([(830,225),(830,265),(508,265)],arrow=True)
d.line([(575,375),(720,375)],arrow=True); d.label(645,365,'[no]',70,11)
d.label(530,417,'[yes]',65,10)
d.line([(850,350),(850,310),(650,310)],arrow=True); d.label(745,300,'[resubmit]',120,10)
d.finish()
para('Server acceptance requires role and assignment checks, a valid transition, meaningful delivery proof or a failure reason, and an atomic parcel + history + outbox commit. Rejection keeps the last confirmed status. Re-attempts are separate Dispatcher/Admin actions.',510,size=9.5)
end()

start('5.3  Photo-proof upload activity diagram','Optional photo branch of the delivery workflow. The browser uploads directly to private object storage; delivery remains pending until the status API validates the uploaded object and accepts the command.')
d=Diagram('12-photo-proof-activity','Figure 7. Authorized upload with a retry path')
d.node(300,12)
d.box(150,35,300,40,'Capture or select a JPEG/PNG|photo, at most 5 MB',12)
d.box(150,110,300,45,'Request upload URL: API checks|role, parcel access and file constraints',12)
d.decision(300,205,170,60,'URL issued?')
d.box(650,180,300,50,'Show authorization / input error;|return to the parcel screen',12)
d.node(800,275,True)
d.box(150,265,300,45,'PUT photo to presigned URL;|show upload progress',12)
d.decision(300,365,200,60,'Upload succeeded?')
d.box(650,340,300,50,'Show upload error; retry|request a fresh URL if expired',12)
d.box(150,425,300,35,'Attach proof reference; submit status',12)
d.node(300,475,True)
for pts in [[(300,18),(300,35)],[(300,75),(300,110)],[(300,155),(300,175)],[(300,235),(300,265)],[(300,310),(300,335)],[(300,395),(300,425)],[(300,460),(300,465)],[(800,230),(800,265)]]: d.line(pts,arrow=True)
d.line([(385,205),(650,205)],arrow=True); d.label(520,195,'[no]',80,11)
d.label(335,254,'[yes]',70,10)
d.line([(400,365),(650,365)],arrow=True); d.label(520,355,'[no]',80,11)
d.label(335,415,'[yes]',70,10)
d.line([(950,365),(980,365),(980,132),(450,132)],arrow=True)
d.label(740,122,'[retry: request URL again]',250,11)
d.finish()
para('The delivery command verifies that the object belongs to this parcel and checks the actual size and image type. A failed or abandoned upload never marks a parcel delivered. Name-only proof skips this photo activity and returns directly to the main workflow.',510,size=9.5)
end()

start('5.4  Courier parcel ER diagram','Relational model for courier operations in parcels_db. Booking-only attributes are omitted. Solid relationships are local foreign keys; cardinalities describe each end of the relationship.')
d=Diagram('09-courier-er','Figure 8. Parcel, status history and delivery proof')
d.entity(20,25,280,'PARCEL',[
 'PK id : UUID','UQ tracking_number','status : ParcelStatus','courier_id : external user ID (nullable)',
 'customer_id : external user ID','merchant_id : external ID (nullable)',
 'pickup_zone_id / dropoff_zone_id','version : optimistic locking','created_at / updated_at',
 '... booking attributes (shared aggregate)'])
d.entity(660,25,320,'PARCEL_STATUS_HISTORY',[
 'PK id : UUID','FK parcel_id -> parcel.id','from_status / to_status','actor_id : external user ID',
 'actor_role','note : nullable, max 500 characters','failure_reason : nullable enum',
 'FK proof_id -> delivery_proof.id (nullable)','lat / lng : nullable','occurred_at'])
d.entity(340,310,300,'DELIVERY_PROOF',[
 'PK id : UUID','FK parcel_id -> parcel.id','type : NAME / PHOTO / SIGNATURE',
 'recipient_name : nullable','object_key : nullable private S3 key','created_at'])
d.line([(300,90),(660,90)])
d.label(480,78,'1 parcel has 0..* history rows',310,12)
d.text(315,112,'1',12); d.text(637,112,'0..*',12)
d.line([(160,255),(160,400),(340,400)])
d.text(180,278,'1',12); d.text(307,390,'0..*',12)
d.label(180,318,'has proof records',190,12)
d.line([(820,255),(820,400),(640,400)])
d.text(846,278,'0..*',12); d.text(675,390,'0..1',12)
d.label(820,318,'optionally references proof',280,12)
d.finish()
para('Each history row and proof belongs to one parcel. A history row may reference one proof; the proof must belong to that same parcel. DELIVERED requires a meaningful recipient name and/or verified photo.',510,size=9.5)
end()

start('5.4  Event persistence and external references','Outbox and deduplication records provide reliable event delivery and assignment processing. Dashed lines below are logical ID references, not database foreign keys or synchronous cross-service joins.')
d=Diagram('10-event-persistence-er','Figure 9. Reliability tables and service data boundaries')
d.entity(20,20,310,'OUTBOX_EVENT',[
 'PK id : UUID','aggregate_id : varchar(64), no FK','event_type / topic / message_key',
 'payload : JSONB','correlation_id : nullable','created_at / published_at (nullable)',
 'attempts / last_error (nullable)'])
d.entity(690,20,290,'PROCESSED_EVENT',[
 'PK event_id : UUID','PK consumer : varchar(100)','processed_at : timestamptz'])
d.entity(365,260,270,'PARCEL (local)',[
 'PK id','courier_id : nullable external user ID','status / version'])
d.box(20,355,270,90,'IDENTITY & ACCESS|user_profile / courier_profile|user_id = authenticated subject',12)
d.box(710,355,270,90,'DISPATCH & WORKLOAD|assignment: parcel_id, courier_id|source: ParcelAssigned event',12)
d.line([(330,100),(500,100),(500,260)],dash=True)
d.label(500,152,'aggregate_id identifies parcel',285,12)
d.label(500,190,'1 parcel : 0..* status outbox rows',300,11)
d.line([(365,315),(310,315),(310,400),(290,400)],dash=True)
d.text(175,335,'courier_id references user_id',11)
d.line([(635,315),(675,315),(675,400),(710,400)],dash=True)
d.text(825,335,'allocation mirrored by event',11)
d.text(835,190,'Composite PK prevents processing the|same event twice for the same consumer.',11)
d.text(500,472,'No FK to another service database. S3 object_key is an object reference, not a relational foreign key.',11)
d.finish()
para('Write status + history + outbox in one transaction. For ParcelAssigned, insert processed_event and update parcel.courier_id in one transaction. outbox_event.aggregate_id is a generic string; processed_event has no parcel_id column and no direct parcel relationship.',510,size=9.5)
end()

start('5.4  Architecture and ownership','Courier Operations and Customer Booking share parcel-service. The status module uses its own service database and integrates through APIs and events.')
d=Diagram('06-module-architecture','Figure 10. Layered module and shared-service boundaries')
for x,y,w,h,s in [(20,30,230,65,'Courier mobile web|run sheet / stepper / proof'),(340,30,300,65,'Next.js BFF + API Gateway|session / JWT / routing'),(340,145,300,65,'api.v1: CourierStatusController|DTO validation + role checks'),(340,250,300,65,'application.command.status|update / re-attempt / proof'),(20,355,260,70,'domain.state + domain.policy|State pattern / ParcelAccessPolicy'),(340,355,300,70,'Infrastructure adapters|PostgreSQL / outbox / S3'),(735,30,245,65,'dispatch-service|GET /courier/run-sheet?date='),(735,145,245,65,'Keycloak|JWT validation / roles'),(735,250,245,65,'PostgreSQL parcels_db|parcel / history / proof / outbox'),(735,355,245,70,'Private RustFS bucket|swiftdrop-proofs')]: d.box(x,y,w,h,s,13)
d.line([(250,62),(340,62)],arrow=True); d.line([(640,62),(735,62)],arrow=True)
d.line([(490,95),(490,145)],arrow=True); d.line([(490,210),(490,250)],arrow=True)
d.line([(340,282),(150,282),(150,355)],arrow=True); d.line([(490,315),(490,355)],arrow=True)
d.line([(640,390),(690,390),(690,282),(735,282)],arrow=True); d.line([(640,410),(735,410)],arrow=True)
d.line([(640,177),(735,177)],dash=True,arrow=True)
d.label(150,320,'framework-free domain',230,11)
d.finish()
para('Layered microservices keep lifecycle rules inside parcel-service. The BFF relays the user token through the gateway; the service enforces role and parcel access. PostgreSQL commits the status and outbox together, while Kafka delivers changes independently to tracking, notifications, webhooks and reporting.',510,size=9.5)
end()

start('5.4  Integration design','One synchronous command path, one reliable event path and one direct object-upload path. Cross-service references use IDs, never foreign keys.')
d=Diagram('07-integration-design','Figure 11. Outbox, assignment consumer and downstream fan-out')
for x,y,w,h,s in [(20,35,220,65,'dispatch-service|allocation outbox'),(390,35,220,65,'Kafka|ParcelAssigned'),(760,35,220,65,'Assignment consumer|dedupe + set courier_id'),(20,210,220,70,'Status command|policy + State validation'),(390,210,220,70,'PostgreSQL transaction|parcel + history + outbox'),(760,210,220,70,'Outbox relay|publish / acknowledge'),(390,365,220,65,'Kafka status topic|key = parcelId'),(20,365,220,65,'tracking-service|notification-service'),(760,365,220,65,'webhook / reporting|dispatch services')]: d.box(x,y,w,h,s,13)
for a,b in [(240,390),(610,760)]: d.line([(a,67),(b,67)],arrow=True)
d.line([(870,100),(870,145),(500,145),(500,210)],arrow=True); d.text(500,170,'processed_event + assignment update in one TX',11)
for a,b in [(240,390),(610,760)]: d.line([(a,245),(b,245)],arrow=True)
d.line([(870,280),(870,325),(500,325),(500,365)],arrow=True)
d.line([(390,398),(240,398)],arrow=True); d.line([(610,398),(760,398)],arrow=True)
d.text(500,465,'At-least-once delivery: eventId deduplication at every consumer; preserve order per parcel.',12)
d.finish(); end()

def table(headers,rows,y,widths):
    x0=42
    for idx,row in enumerate([headers]+rows):
        font='Helvetica-Bold' if idx==0 else 'Helvetica'; size=9.3
        wrapped=[sum([simpleSplit(part,font,size,w-16) for part in s.split('\n')],[]) for s,w in zip(row,widths)]
        h=max(len(a) for a in wrapped)*13+16
        c.setFillColorRGB(*((.94,.95,.96) if idx==0 else (1,1,1))); c.setStrokeColorRGB(.82,.84,.87)
        c.rect(x0,H-y-h,sum(widths),h,stroke=1,fill=1)
        x=x0
        for lines,w in zip(wrapped,widths):
            for k,line in enumerate(lines): txt(x+8,y+15+k*13,line,size,idx==0)
            x+=w
        y+=h
    return y

start('5.4  Contracts, security and persistence','The model below covers courier operations in parcels_db. Customer Booking owns booking and cancellation; Dispatch & Workload owns allocation and the run sheet.')
table(['BOUNDARY','DESIGN'],[
 ('Status command','PATCH /api/v1/courier/parcels/{id}/status: target status, note, failureReason, proof and optional coarse location. Note max 500 characters per event schema.'),
 ('Authorization','Require courier or authorized staff. Assigned courier ID must match the authenticated subject. Apply the same ownership rule to reads, updates, upload URLs and proof viewing. Foreign courier -> 404; customer -> 403.'),
 ('Re-attempt','Dedicated dispatcher/admin command: FAILED -> OUT_FOR_DELIVERY. The action validates the current state and records the re-attempt in history and the outbox.'),
 ('ParcelStatusChanged','Topic swiftdrop.parcel.status-changed.v1; envelope includes eventId, eventType, eventVersion=1, occurredAt, producer and data. Data requires parcelId, trackingNumber, toStatus, changedBy and changedAt.'),
 ('Failure / proof','Reasons: RECIPIENT_UNAVAILABLE, WRONG_ADDRESS, REFUSED, DAMAGED, OTHER. Proof schema supports NAME, PHOTO, SIGNATURE; Courier Operations implements recipient name and/or photo. Service validation must enforce meaningful proof, beyond a non-null object.'),
 ('Transactional data','parcel(status, courier_id, version); parcel_status_history(parcel_id, from_status, to_status, actor, note, failure_reason, proof_id, occurred_at); delivery_proof(parcel_id, type, recipient_name, object_key); outbox_event; processed_event for assignments.'),
 ('Privacy / consistency','No presigned URL or photo bytes in Kafka. Carry proofId; authorize viewers and issue expiring GET URLs. Consume ParcelAssigned idempotently and in parcel order so a replay cannot restore an old allocation.')
],126,[145,613])
end()

start('5.5  Design patterns and principles','State pattern owns transition rules; application services own transaction orchestration. The domain depends on abstractions, keeping Spring, JPA, Kafka and S3 outside it.')
d=Diagram('08-state-pattern','Figure 12. State pattern and application ports')
for x,y,w,h,s in [(20,35,260,70,'CourierStatusController|updateStatus(command, user)'),(365,35,270,70,'UpdateParcelStatusHandler|authorize / validate / commit'),(720,35,260,70,'ParcelAccessPolicy|requireAssignedOrStaff()'),(365,190,270,75,'Parcel aggregate|status: ParcelState|transitionTo(target, context)'),(20,355,265,75,'<<interface>> ParcelState|validate(target, context)|allowedNextStates()'),(365,355,270,75,'Concrete states|Created / PickedUp / InTransit|OutForDelivery / Failed / terminals'),(720,190,260,75,'<<ports>>|ParcelRepository / OutboxPort|ProofStorage'),(720,355,260,75,'<<adapters>>|JPA / Kafka outbox relay|S3ProofStorage (AWS SDK v2)')]: d.box(x,y,w,h,s,13)
d.line([(280,70),(365,70)],arrow=True); d.line([(635,70),(720,70)],arrow=True)
d.line([(500,105),(500,190)],arrow=True)
d.line([(635,90),(675,90),(675,227),(720,227)],arrow=True)
d.line([(365,227),(153,227),(153,355)],arrow=True); d.text(230,280,'delegates validation',11)
d.realizes([(365,393),(285,393)]); d.text(325,345,'implements',11)
d.realizes([(850,355),(850,265)]); d.text(910,315,'implements',11)
d.text(500,468,'State validation includes DELIVERED proof, FAILED reason and staff-only re-attempt guards.',12)
d.finish(); end()

start('5.5  Design patterns and principles')
table(['PATTERN / PRINCIPLE','APPLICATION IN COURIER OPERATIONS'],[
 ('State','Each concrete state exposes valid next states and validates guards. UI next-step options reflect backend rules; the backend remains authoritative.'),
 ('Adapter + Repository','S3ProofStorage implements ProofStorage; persistence adapters hide JPA. Controllers translate HTTP requests into application commands.'),
 ('Transactional outbox + pub/sub','Atomic status/history/outbox commit prevents lost events from dual writes. Kafka fans out independently to tracking, webhooks, notifications, reporting and dispatch.'),
 ('Idempotent consumer','Record ParcelAssigned eventId and assignment update atomically. Downstream services likewise deduplicate ParcelStatusChanged.'),
 ('Facade / BFF','Next.js route handlers keep access tokens server-side and provide the courier interface with authenticated access to parcel and run-sheet APIs.')
],104,[174,584])
txt(42,372,'Principles',13,True)
para('Single responsibility: controllers handle HTTP, application handlers coordinate transactions, state objects enforce lifecycle rules and storage adapters manage proof objects.',395,size=10)
para('Dependency inversion: application code depends on repository and storage ports. S3ProofStorage and persistence adapters supply the infrastructure implementations.',435,size=10)
para('Open/closed and DRY: new event consumers can be added without changing the status producer. Authentication, logging and outbox behavior are shared through common libraries.',475,size=10)
para('Least privilege and configuration: parcel access follows the authenticated role and assignment. Storage endpoints, credentials and other environment-specific settings are external configuration.',515,size=9.5)
end()

c.save()
print(OUT)
print(f'{page_no} pages; 12 editable SVG diagrams')
