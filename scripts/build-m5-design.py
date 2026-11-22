#!/usr/bin/env python3
"""Generate the M5 design note and editable SVG diagrams (reportlab required)."""
from pathlib import Path
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_LEFT
from reportlab.graphics.shapes import Drawing, Rect, Ellipse, Line, String, Circle, Polygon
from reportlab.graphics import renderSVG
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, PageBreak

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'doc/diagrams/m5'
BLUE = colors.HexColor('#0b5ed7')
INK = colors.HexColor('#172b4d')
PALE = colors.HexColor('#eef7ff')


def label(d, x, y, text, size=10):
    d.add(String(x, y, text, fontName='Helvetica', fontSize=size, fillColor=INK, textAnchor='middle'))


def arrow(d, x1, y1, x2, y2):
    d.add(Line(x1, y1, x2, y2, strokeColor=BLUE, strokeWidth=1.3))
    import math
    angle = math.atan2(y2-y1, x2-x1)
    points = [x2, y2]
    for a in (angle+2.65, angle-2.65):
        points.extend([x2+7*math.cos(a), y2+7*math.sin(a)])
    d.add(Polygon(points, fillColor=BLUE, strokeColor=BLUE))


def actor(d, x, y, name):
    d.add(Circle(x,y+25,9,strokeColor=INK,fillColor=None))
    for a,b,c,e in [(x,y+16,x,y-14),(x-17,y+3,x+17,y+3),(x,y-14,x-14,y-34),(x,y-14,x+14,y-34)]:
        d.add(Line(a,b,c,e,strokeColor=INK))
    label(d,x,y-51,name)


def usecase():
    d = Drawing(480,350)
    d.add(Rect(118,15,252,320,rx=12,strokeColor=BLUE,fillColor=PALE))
    label(d,244,314,'SwiftDrop dispatch',12)
    actor(d,54,223,'Dispatcher')
    actor(d,426,90,'Courier')
    cases = [(267,'View unassigned parcels'),(210,'Assign / reassign parcel'),(153,'Monitor courier workloads'),(83,'View own daily run sheet')]
    for y,text in cases:
        d.add(Ellipse(244,y,104,20,fillColor=colors.white,strokeColor=BLUE))
        label(d,244,y-3,text)
    for y,_ in cases[:3]: d.add(Line(72,223,140,y,strokeColor=INK))
    d.add(Line(405,90,348,83,strokeColor=INK))
    label(d,54,148,'ADMIN also',8)
    label(d,54,135,'has staff access',8)
    return d


def activity():
    d = Drawing(480,570)
    d.add(Circle(225,562,6,fillColor=INK,strokeColor=INK))
    steps = [(525,'Select unassigned parcel / existing run'),(471,'Inspect zone matches and courier load'),(417,'Choose courier and confirm'),(363,'Validate role, courier and run date'),(309,'Lock parcel; inspect current allocation')]
    last = 556
    for y,text in steps:
        arrow(d,225,last,225,y+16)
        d.add(Rect(40,y-16,370,32,rx=7,fillColor=PALE,strokeColor=BLUE))
        label(d,225,y-3,text,10)
        last=y-16
    arrow(d,225,last,225,277)
    d.add(Polygon([225,277,307,255,225,233,143,255],fillColor=PALE,strokeColor=BLUE))
    label(d,225,252,'Allocation conflict?',9)
    arrow(d,307,255,447,255)
    label(d,369,267,'Yes: 409 / choose again',8)
    d.add(Line(447,255,447,417,strokeColor=BLUE))
    arrow(d,447,417,410,417)
    arrow(d,225,233,225,211)
    label(d,241,218,'No',8)
    last=211
    for y,text in [(195,'Save allocation + outbox in one transaction'),(141,'Publish ParcelAssigned through Kafka'),(87,'Refresh workloads, board and own run sheets')]:
        if y != 195: arrow(d,225,last,225,y+16)
        d.add(Rect(40,y-16,370,32,rx=7,fillColor=PALE,strokeColor=BLUE))
        label(d,225,y-3,text,10)
        last=y-16
    arrow(d,225,last,225,40)
    d.add(Circle(225,31,9,fillColor=None,strokeColor=INK))
    d.add(Circle(225,31,5,fillColor=INK,strokeColor=INK))
    return d


def main():
    OUT.mkdir(parents=True,exist_ok=True)
    uc, act = usecase(), activity()
    renderSVG.drawToFile(uc,str(OUT/'dispatcher-use-case.svg'))
    renderSVG.drawToFile(act,str(OUT/'courier-allocation-activity.svg'))
    styles = getSampleStyleSheet()
    styles.add(ParagraphStyle(name='Kicker',fontName='Helvetica-Bold',fontSize=10,textColor=BLUE,spaceAfter=12))
    styles['Title'].textColor=INK; styles['Title'].alignment=TA_LEFT
    styles['BodyText'].fontSize=10.5; styles['BodyText'].leading=15; styles['BodyText'].spaceAfter=10
    story=[]
    def text(s,style='BodyText'): story.append(Paragraph(s,styles[style]))
    text('SWIFTDROP / M5 / DESIGN NOTE','Kicker')
    text('Dispatch and courier workload','Title')
    text('CQRS read model, transactional allocation and idempotent event handling','Heading2')
    text('The parcel service owns booking and lifecycle commands. Dispatch owns courier allocation commands and an event-fed parcel_summary read model in dispatch_db. Kafka facts update the projection; the board never queries another service database.')
    text('Atomic assignment and concurrency','Heading2')
    text('An assignment locks the parcel row, rereads its allocation using a fresh READ COMMITTED snapshot, validates availability and state, then writes assignment and ParcelAssigned to the shared outbox in one transaction. A unique parcel constraint backs the row lock. Competing initial allocations return 409. Reassignment retains the assignment ID and run date, sets previousCourierId and atomically moves ownership between run sheets.')
    text('Replay and event ordering','Heading2')
    text('IdempotentConsumer stores the eventId and projection changes in the same transaction. A rollback removes both. A second delivery makes no changes. Kafka orders only within a topic partition, so a status can precede creation: dispatch stores a placeholder and creation later fills booking fields without resetting status. Status timestamps prevent older facts from overwriting newer state. Failed processing retains its offset for retry.')
    text('Workload and authorization','Heading2')
    text('courier_workload is a SQL view over assignment and parcel_summary, so replay cannot inflate an incremental counter. Active excludes delivered, failed and cancelled parcels. Today means Asia/Colombo; delivered and failed counts describe current parcel outcomes whose latest status occurred today. Capacity is advisory. Names and capacity are joined from M1 using the caller token. Staff endpoints require DISPATCHER or ADMIN; courier run sheets require COURIER and always filter by JWT sub.')
    text('Live board and verification','Heading2')
    text('M6 Socket.IO dispatch activity invalidates board data after a brief delay; a five-second refresh covers projection lag and disconnection. The courier sheet refreshes on the same interval. Testcontainers exercises Kafka and Postgres, outbox publication, duplicates, ordering, transaction rollback, reassignment, concurrency and courier isolation. Unit/API and React tests check allocation validation, role rules and UI actions.')
    story.append(PageBreak())
    text('Dispatch interactions','Title'); story.append(uc)
    text('Dispatcher use cases','Heading2')
    text('Dispatchers and administrators inspect the unassigned pool, compare workloads, assign and reassign. Couriers view only their own daily run sheet. Full addresses and contact details do not travel through this projection.')
    story.append(PageBreak())
    text('Courier allocation activity','Title'); story.append(act)
    text('Failure handling','Heading2')
    text('Invalid requests return problem+json. Existing allocations, closed parcels and same-courier reassignment return 409; unavailable couriers and past run dates return 422. Kafka lag is visible in the M5 Grafana dashboard using M9 Kafka Exporter metrics.')
    def footer(canvas, doc):
        canvas.setFont('Helvetica',8);canvas.setFillColor(INK)
        canvas.drawString(42,24,'SwiftDrop | M5 dispatch-service | Issue 18')
        canvas.drawRightString(553,24,str(doc.page))
    SimpleDocTemplate(str(ROOT/'doc/m5-dispatch-design.pdf'),pagesize=(595,842),rightMargin=42,leftMargin=42,topMargin=42,bottomMargin=42).build(story,onFirstPage=footer,onLaterPages=footer)

if __name__ == '__main__': main()
