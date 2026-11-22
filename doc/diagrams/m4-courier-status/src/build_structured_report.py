"""Build the courier report using the supplied six-topic report structure."""
from pathlib import Path
from html import escape
import math
import re
import xml.etree.ElementTree as ET
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4, landscape
from reportlab.lib.colors import HexColor, toColor
from reportlab.lib.utils import simpleSplit

ROOT=Path(__file__).resolve().parents[4]
FIG=Path(__file__).resolve().parents[1]
OUT=ROOT/'output/pdf/SwiftDrop_Courier_Operations_Design.pdf'
OUT.parent.mkdir(parents=True,exist_ok=True)
W,H=landscape(A4)
c=canvas.Canvas(str(OUT),pagesize=(W,H))
c.setTitle('Courier Operations and Status Lifecycle')
c.setAuthor('Pamindu Piyumaka')
page=0
export_pages=[]

def text(x,y,s,size=11,bold=False,color='#17191c',align='left'):
    c.setFillColor(HexColor(color)); c.setFont('Helvetica-Bold' if bold else 'Helvetica',size)
    getattr(c,{'left':'drawString','center':'drawCentredString','right':'drawRightString'}[align])(x,H-y,s)

def para(s,y,size=10.5,width=W-84):
    for line in simpleSplit(s,'Helvetica',size,width):
        text(42,y,line,size); y+=size*1.48
    return y

def start(title,intro=''):
    global page
    page+=1
    text(42,28,'SWIFTDROP  /  DESIGN DOCUMENTATION',8,True,'#626974')
    c.setStrokeColor(HexColor('#d7dce2')); c.line(42,H-39,W-42,H-39)
    text(42,67,title,19,True)
    text(42,H-23,'Pamindu Piyumaka',8,color='#626974')
    text(W-42,H-23,f'Page {page}',8,color='#626974',align='right')
    return para(intro,94) if intro else 94

def end(): c.showPage()

def path(points,fill=False):
    p=c.beginPath(); p.moveTo(*points[0])
    for q in points[1:]: p.lineTo(*q)
    if fill: p.close()
    c.drawPath(p,stroke=1,fill=int(fill))

def vector(name,x,y,w):
    root=ET.parse(FIG/(name+'.svg')).getroot()
    scale=w/1000
    c.saveState(); c.translate(x,H-y); c.scale(scale,-scale)
    for el in root:
        tag=el.tag.split('}')[-1]; a=el.attrib
        fill=a.get('fill','#17191c' if tag=='text' else 'none'); stroke=a.get('stroke','none')
        c.setFillColor(toColor(fill) if fill!='none' else HexColor('#ffffff'))
        c.setStrokeColor(toColor(stroke) if stroke!='none' else HexColor('#ffffff'))
        c.setLineWidth(float(a.get('stroke-width',1)))
        c.setDash([float(v) for v in a.get('stroke-dasharray','').replace(',',' ').split()])
        kw={'fill':int(fill!='none'),'stroke':int(stroke!='none')}
        n=lambda key,default=0:float(a.get(key,default))
        if tag=='rect': c.roundRect(n('x'),n('y'),n('width'),n('height'),n('rx'),**kw)
        elif tag=='circle': c.circle(n('cx'),n('cy'),n('r'),**kw)
        elif tag=='ellipse': c.ellipse(n('cx')-n('rx'),n('cy')-n('ry'),n('cx')+n('rx'),n('cy')+n('ry'),**kw)
        elif tag in ('polyline','polygon'):
            nums=[float(z) for z in re.findall(r'-?\d+(?:\.\d+)?',a['points'])]
            pts=list(zip(nums[::2],nums[1::2])); p=c.beginPath(); p.moveTo(*pts[0])
            for q in pts[1:]: p.lineTo(*q)
            if tag=='polygon': p.close()
            c.drawPath(p,**kw)
        elif tag=='text':
            c.saveState(); c.translate(n('x'),n('y')); c.scale(1,-1)
            c.setFont('Helvetica-Bold' if a.get('font-weight')=='bold' else 'Helvetica',n('font-size',13))
            fn={'middle':c.drawCentredString,'end':c.drawRightString}.get(a.get('text-anchor'),c.drawString)
            fn(0,0,el.text or ''); c.restoreState()
    c.restoreState()
    return 480*scale

def figure(section,intro,name,title,caption):
    y=start(section,intro)+12
    for line in simpleSplit(title,'Helvetica-Bold',11,W-96):
        text(48,y,line,11,True); y+=15
    y+=5
    width=min(W-100,(510-y)*1000/480)
    h=vector(name,(W-width)/2,y,width)
    export_pages.append((page,name,(W-width)/2,y,width,h))
    para(caption,y+h+19,9.5)
    end()

class SVG:
    def __init__(self): self.s=[]
    def text(self,x,y,s,size=13,bold=False):
        for i,line in enumerate(s.split('|')):
            self.s.append(f'<text x="{x}" y="{y+i*size*1.3}" font-size="{size}" text-anchor="middle" font-family="Arial" fill="#17191c"'+(' font-weight="bold"' if bold else '')+'>'+escape(line)+'</text>')
    def box(self,x,y,w,h,s,size=13):
        self.s.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="5" fill="white" stroke="#222"/>')
        self.text(x+w/2,y+h/2-(len(s.split('|'))-1)*size*.65+size*.3,s,size)
    def line(self,pts,label='',lx=0,ly=0,dash=False):
        self.s.append('<polyline points="'+' '.join(f'{x},{y}' for x,y in pts)+'" fill="none" stroke="#222"'+(' stroke-dasharray="4 3"' if dash else '')+'/>')
        x,y=pts[-1]; a,b=pts[-2]; t=math.atan2(y-b,x-a)
        self.s.append('<polyline points="'+' '.join(f'{u},{v}' for u,v in [(x-8*math.cos(t-.45),y-8*math.sin(t-.45)),(x,y),(x-8*math.cos(t+.45),y-8*math.sin(t+.45))])+'" fill="none" stroke="#222"/>')
        if label: self.text(lx,ly,label,10)
    def save(self,name):
        (FIG/(name+'.svg')).write_text('<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="480" viewBox="0 0 1000 480"><rect width="1000" height="480" fill="white"/>'+''.join(self.s)+'</svg>',encoding='utf-8')

d=SVG()
d.text(500,20,'ASYNCHRONOUS: STATUS EVENTS AND ASSIGNMENT UPDATES',12,True)
for x,y,w,h,s in [(20,45,210,70,'Parcel status command|validate and apply transition'),(300,45,210,70,'4 - Transactional outbox|parcel + history + event'),(580,45,160,70,'10 - Outbox relay|publish / retry'),(800,45,180,70,'5 / 6 - Kafka|pub/sub, parcelId key'),(20,190,210,70,'Dispatch allocation|ParcelAssigned via Kafka'),(300,190,210,70,'7 - Idempotent consumer|processed_event + courier_id'),(800,190,180,70,'8 / 9 - Consumers|groups + read models'),(20,355,210,65,'1 - Courier browser|request / reply'),(300,355,210,65,'2 - BFF and gateway|server-side token relay'),(580,355,160,65,'Status / proof API|validate command'),(800,355,180,65,'3 - Private RustFS|presigned object access')]: d.box(x,y,w,h,s,12)
for pts in [[(230,80),(300,80)],[(510,80),(580,80)],[(740,80),(800,80)],[(890,115),(890,190)],[(230,225),(300,225)],[(405,190),(405,115)],[(230,388),(300,388)],[(510,388),(580,388)],[(740,388),(800,388)]]: d.line(pts)
d.text(500,315,'SYNCHRONOUS: COURIER COMMANDS AND PRIVATE OBJECT ACCESS',12,True)
d.line([(125,420),(125,460),(890,460),(890,420)])
d.text(500,451,'3 - Browser uploads image bytes directly; authorized viewers use an expiring GET URL',11)
d.save('13-numbered-integration')

d=SVG()
d.text(500,20,'CLIENT AND APPLICATION TIERS',12,True)
for x,y,w,h,s in [(20,55,260,70,'Courier mobile browser|phone camera / run sheet'),(365,55,270,70,'Next.js BFF + API gateway|Auth.js session / JWT routing'),(720,55,260,70,'parcel-service :8084|Java 21 / Spring Boot 4'),(20,210,260,70,'Keycloak|OIDC login / JWT signing keys'),(365,210,270,70,'dispatch-service :8085|allocations / run-sheet API'),(720,210,260,70,'PostgreSQL parcels_db|status / history / proof / outbox'),(20,365,260,70,'Kafka|parcel and assignment topics'),(365,365,270,70,'RustFS :9010|private swiftdrop-proofs bucket'),(720,365,260,70,'Observability|Prometheus / Grafana / logs')]: d.box(x,y,w,h,s,12)
for pts in [[(280,90),(365,90)],[(635,90),(720,90)],[(500,125),(500,210)],[(850,125),(850,210)],[(365,110),(320,110),(320,245),(280,245)],[(720,110),(675,110),(675,325),(150,325),(150,365)],[(720,110),(690,110),(690,345),(500,345),(500,365)],[(980,90),(995,90),(995,400),(980,400)]]: d.line(pts)
d.text(500,474,'Docker Compose locally; Kubernetes deployment uses shared configuration, secrets, health probes and service endpoints.',11)
d.save('14-deployment-scaling')

start('COURIER OPERATIONS AND STATUS LIFECYCLE')
text(42,108,'Pamindu Piyumaka',13,True)
para('This section describes the courier operations and parcel status lifecycle module. Couriers open their assigned run sheet, update parcels through valid stages, record proof of delivery and report failed attempts. parcel-service records each accepted change with its history and a ParcelStatusChanged event, which drives tracking, notifications, webhooks, dispatch workload and reporting.',148,12,width=710)
text(42,245,'Service  parcel-service     Data  PostgreSQL / RustFS / Kafka',10.5,True)
for i,s in enumerate(['1. USE CASES','2. DATA MODEL','3. ARCHITECTURE','4. INTEGRATION DESIGN','5. KEY FLOWS','6. DESIGN PATTERNS AND PRINCIPLES']): text(42,300+i*32,s,13)
end()

figure('1. USE CASES',
 'This section describes the main functionalities of the module and who uses them. The assigned courier views the daily run sheet and parcel details, records status progress, supplies delivery proof and reports a failed attempt. A dispatcher or administrator authorizes a re-attempt.',
 '01-courier-use-cases','Use Case Diagram - Courier Operations',
 'Proof capture and failure details are conditional branches of the status-update use case. The courier sees only assigned parcels. A re-attempt returns a failed parcel to OUT_FOR_DELIVERY; cancellation is part of Customer Booking.')
figure('2. DATA MODEL',
 'This section presents the data structure in PostgreSQL parcels_db. PARCEL stores the current status and courier assignment. PARCEL_STATUS_HISTORY records the actor and every accepted transition. DELIVERY_PROOF stores the recipient name or private object key associated with a delivery.',
 '09-courier-er','ER Diagram - Parcels, Status History and Delivery Proof',
 'One parcel has many history rows and proof records. A history row may reference one proof belonging to the same parcel. User, merchant and zone identifiers refer to other services by ID only. Photo bytes remain in RustFS, while PostgreSQL stores their metadata.')
figure('2. DATA MODEL',
 'OUTBOX_EVENT stores the event in the same transaction as the parcel change. PROCESSED_EVENT has a composite event_id and consumer key so assignment-event duplicates are handled once. Cross-service references do not introduce foreign keys into another service database.',
 '10-event-persistence-er','ER Diagram - Event Persistence and Service References',
 'The assignment consumer updates courier_id and inserts its processed-event marker atomically. aggregate_id in the generic outbox identifies the parcel without a foreign key. A delivery proof object_key refers to private storage, not to a relational table.')
figure('3. ARCHITECTURE',
 'This section explains parcel-service, a Spring Boot service on port 8084 behind the API gateway. Controllers accept courier commands, application handlers coordinate transactions, domain states enforce transitions and infrastructure adapters provide persistence, event delivery and object storage.',
 '06-module-architecture','Architecture Diagram - Courier Status Module',
 'The layered design keeps business rules separate from Spring, JPA, Kafka and S3. The Next.js BFF keeps tokens server-side; gateway and service validate JWTs. Dispatch provides the run sheet, while the parcel module owns status, proof and history writes.')
figure('4. INTEGRATION DESIGN',
 'This section describes integration with courier clients, allocation events and downstream services. The upper path handles committed status changes and assignment messages. The lower path handles authenticated commands and direct photo access. Numbers match the following table.',
 '13-numbered-integration','Integration Diagram - Numbered Patterns',
 'The outbox separates database commits from Kafka availability. Consumers receive events independently and deduplicate by eventId. Direct presigned uploads keep photo bytes out of the application request path, the outbox and Kafka.')

patterns=[
 ('1','Request/reply','Courier browser to parcel APIs','A status update or upload-URL request needs an immediate accepted or rejected result.'),
 ('2','Gateway and BFF token relay','Next.js route handlers and API gateway','The browser uses its session; server-side requests carry the user access token.'),
 ('3','Presigned object access','Browser and private RustFS bucket','Images upload directly using expiring PUT URLs; authorized viewers receive expiring GET URLs.'),
 ('4','Transactional outbox','parcel-service and parcels_db','Parcel status, history and the outgoing event commit together.'),
 ('5','Publish/subscribe','Kafka status-change topic','Tracking, notifications, webhooks, dispatch and reporting react independently.'),
 ('6','Partitioning by key','Kafka message key = parcelId','Events published in order for one parcel are routed to the same partition.'),
 ('7','Idempotent consumer','ParcelAssigned consumer and processed_event','Repeated event delivery must not repeat an assignment side effect.'),
 ('8','Competing consumers','Replicas within each Kafka consumer group','Partitions distribute processing across replicas; each service has its own group.'),
 ('9','CQRS read models','Tracking, reporting and dispatch projections','Consumers build their own views instead of joining across service databases.'),
 ('10','Retry / at-least-once delivery','Shared outbox relay','Unpublished events are retried; duplicate publications are handled by consumers.')]
start('4. INTEGRATION DESIGN', 'The following table summarizes the integration patterns shown in the diagram.')
widths=[26,153,218,361]; y=130
for idx,row in enumerate([('#','Pattern','Where used','Why')]+patterns):
    font='Helvetica-Bold' if idx==0 else 'Helvetica'; size=9
    lines=[simpleSplit(s,font,size,w-14) for s,w in zip(row,widths)]
    h=max(map(len,lines))*12+13
    c.setFillColor(HexColor('#f0f2f4' if idx==0 else '#ffffff')); c.setStrokeColor(HexColor('#d5dae1'))
    c.rect(42,H-y-h,sum(widths),h,fill=1,stroke=1)
    x=42
    for cell,w in zip(lines,widths):
        for k,line in enumerate(cell): text(x+7,y+14+k*12,line,size,idx==0)
        x+=w
    y+=h
assert y<555,y
end()

figure('5. KEY FLOWS',
 'This section illustrates the main workflows using state, sequence and activity diagrams. The lifecycle constrains every command: normal courier progress is sequential, delivery needs proof, a failed attempt needs a reason and only authorized staff initiate a re-attempt.',
 '02-parcel-state','State Diagram - Parcel Status Lifecycle',
 'CREATED -> DELIVERED is rejected because it skips intermediate states. DELIVERED and CANCELLED are terminal. Courier assignment is an attribute, not a parcel status. FAILED -> OUT_FOR_DELIVERY is reserved for an authorized re-attempt.')
figure('5. KEY FLOWS',
 'The courier submits PATCH /api/v1/courier/parcels/{id}/status with a target status, optional note and location, and the required proof or failure reason. The service checks the authenticated role, assignment and current state before accepting the command.',
 '03-status-update-sequence','Sequence Diagram - Courier Status Update and Outbox',
 'The transaction records parcel status, history and outbox together. Courier access to another courier\'s parcel returns 404, while a customer calling courier endpoints receives 403. Version checks or locking protect the transition against concurrent updates.')
figure('5. KEY FLOWS',
 'After the status transaction commits, the outbox relay publishes ParcelStatusChanged. Independent consumers update the tracking timeline, deliver notifications, queue merchant webhooks and update delivery statistics. No consumer is part of the courier request transaction.',
 '04-event-consumers-sequence','Sequence Diagram - Status Event to Tracking, Notifications and Webhooks',
 'Tracking pushes a live update through Socket.IO. Notifications use email and mock SMS. Webhooks are signed and retried by webhook-service. Reporting updates its own projections. Consumer deduplication prevents repeated side effects from repeated event delivery.')
figure('5. KEY FLOWS',
 'The courier requests POST /api/v1/courier/parcels/{id}/proof-upload-url. The service checks parcel access and image constraints, and returns a short-lived PUT URL for a random private object key. The browser uploads the photo directly to RustFS.',
 '05-proof-upload-sequence','Sequence Diagram - Photo Upload and Delivery Confirmation',
 'JPEG and PNG uploads are limited to 5 MB. The delivery command checks the uploaded object and binds proof to the same parcel before committing. Viewers are authorized before an expiring GET URL is issued. Neither photo bytes nor presigned URLs travel in status events.')
figure('5. KEY FLOWS',
 'The courier selects a parcel and a valid next status. A normal progress update may include a note. DELIVERED requires a recipient name and/or photo proof; FAILED requires an enum reason. The same status command handles each branch after validation.',
 '11-courier-workflow-activity','Activity Diagram - Courier Status Update',
 'Rejected commands retain the last confirmed status and display an error so details can be corrected or stale data refreshed. The screen confirms a change only after the server accepts it. Re-attempts remain a separate dispatcher or administrator action.')
figure('5. KEY FLOWS',
 'This activity shows the optional photo branch of the delivery workflow. It includes file selection, upload-URL authorization, direct PUT progress, upload failure and retry. Name-only proof skips this activity and continues with the normal delivery command.',
 '12-photo-proof-activity','Activity Diagram - Photo Proof Upload',
 'An expired URL is replaced before another upload attempt. Authorization failures return the courier to the parcel screen. A failed or abandoned upload never completes delivery; the status API validates the object when the final command is submitted.')
figure('6. DESIGN PATTERNS AND PRINCIPLES',
 'This section identifies the design patterns and software engineering principles used by the courier module. The State pattern owns lifecycle rules, application handlers coordinate the use cases, access policy enforces assignment and ports keep infrastructure separate.',
 '08-state-pattern','Design Pattern in Courier Status Module',
 'Controllers delegate to application handlers. The parcel delegates transition checks to ParcelState implementations. Repository and storage ports are implemented by technology-specific adapters. Hollow triangles show interface realization; arrows show dependencies.')
figure('6. DESIGN PATTERNS AND PRINCIPLES',
 'Deployment and Scaling. The courier interface runs in apps/web and parcel-service runs as a non-root Java container. PostgreSQL, Kafka and RustFS are network services; proof data is never kept on the application container filesystem.',
 '14-deployment-scaling','Deployment and Scaling',
 'The Kubernetes target uses service endpoints, external configuration, secrets and health probes. API replicas can share the database, but relay coordination must preserve per-parcel publication order. Kafka consumer groups share partitions across replicas. RustFS needs durable storage and browser-accessible presigned URLs.')

def numbered(title,intro,items,subheading):
    y=start(title,intro)+15
    text(42,y,subheading,13,True); y+=25
    for i,(label,body) in enumerate(items,1):
        text(42,y,f'{i}. {label}',11,True); y+=18
        y=para(body,y,10.5)+12
    assert y<560,(title,y)
    end()

numbered('6. DESIGN PATTERNS AND PRINCIPLES',
 'The following list summarizes the design patterns and explains how each supports the courier operations module.',[
 ('State Pattern','ParcelState implementations define valid next statuses and enforce delivery-proof, failure-reason and staff-only re-attempt guards.'),
 ('Observer / Pub-Sub Pattern','ParcelStatusChanged is consumed independently by tracking, notifications, webhooks, dispatch and reporting; the parcel producer does not call them directly.'),
 ('Repository and Ports Pattern','ParcelRepository, proof metadata persistence and the outbox interface hide database operations from application use cases.'),
 ('Adapter Pattern','S3ProofStorage implements the proof storage port with the AWS SDK v2 S3 client. HTTP controllers and messaging consumers adapt external requests to application commands.'),
 ('Transactional Outbox','Status, history and the outgoing event are written atomically. The relay publishes committed rows and marks them published after broker acknowledgement.'),
 ('Idempotent Consumer','ParcelAssigned processing records the event and courier assignment in one transaction, preventing duplicate side effects.'),
 ('Facade / BFF Pattern','Next.js route handlers provide the courier screen with authenticated access to parcel and run-sheet APIs while retaining access tokens server-side.')], 'Design Patterns')
numbered('6. DESIGN PATTERNS AND PRINCIPLES',
 'Design Principles. The following principles guide the boundaries, security and reliability of courier operations.',[
 ('Single Responsibility Principle','Controllers handle HTTP, handlers coordinate transactions, state objects validate the lifecycle and storage adapters manage private objects.'),
 ('Open/Closed Principle','New event consumers can react to status changes without changing the status producer. Infrastructure adapters can evolve behind stable ports.'),
 ('Dependency Inversion Principle','Application logic depends on repository and storage abstractions. JPA, Kafka and S3 remain infrastructure details.'),
 ('DRY','AuthenticatedUser, JWT validation, ProblemDetail errors, correlation IDs and outbox behavior use shared Java libraries.'),
 ('Least Privilege Principle','A courier operates on assigned parcels. Unauthorized foreign resources return 404; customer roles cannot call courier endpoints. Re-attempts require authorized staff.'),
 ('Privacy by Design','Photos are private; viewers receive expiring URLs. Events carry proof references and coarse location, with no image bytes, presigned URLs or full street addresses.'),
 ('Secure by Default','Upload requests validate access and file constraints; final status commands validate uploaded objects and bind them to the correct parcel.'),
 ('Twelve-Factor Configuration','Database, Kafka, S3 endpoint and credential settings come from environment configuration or deployment secrets rather than source code.')], 'Design Principles')
c.save()
import json
(FIG/'src/structured_export_pages.json').write_text(json.dumps(export_pages),encoding='utf-8')
print(f'{OUT}\n{page} pages; six main topics; {len(export_pages)} diagrams')
