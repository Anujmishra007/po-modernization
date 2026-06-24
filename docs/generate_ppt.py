#!/usr/bin/env python3
"""
Generate PO Modernization CTO Presentation in PowerPoint format
Maersk Design System: Blue/White theme with Maersk fonts and animations
"""

from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.dml import MSO_LINE_DASH_STYLE
from pptx.oxml.ns import nsmap, qn
from pptx.oxml import parse_xml
from lxml import etree
import os

# ============================================================
# MAERSK DESIGN SYSTEM - BLUE/WHITE THEME ONLY
# ============================================================
MAERSK_BLUE = RGBColor(0x00, 0x24, 0x3D)       # #00243D - Primary Dark Blue
MAERSK_CYAN = RGBColor(0x42, 0xB0, 0xD5)       # #42B0D5 - Accent Cyan
MAERSK_LIGHT_BLUE = RGBColor(0x0A, 0x4D, 0x7C) # #0A4D7C - Medium Blue
WHITE = RGBColor(0xFF, 0xFF, 0xFF)             # #FFFFFF - White
LIGHT_GRAY = RGBColor(0xF8, 0xF9, 0xFA)        # #F8F9FA - Very light gray
CARD_WHITE = RGBColor(0xFF, 0xFF, 0xFF)        # White cards
TEXT_DARK = RGBColor(0x00, 0x24, 0x3D)         # Dark blue text
TEXT_GRAY = RGBColor(0x6C, 0x75, 0x7D)         # #6C757D - Muted gray
BORDER_LIGHT = RGBColor(0xDE, 0xE2, 0xE6)      # #DEE2E6 - Light border

# Fonts
FONT_HEADLINE = "Maersk Headline"
FONT_TEXT = "Maersk Text"


def set_shape_fill(shape, color):
    """Set solid fill color for a shape"""
    fill = shape.fill
    fill.solid()
    fill.fore_color.rgb = color


def add_text_box(slide, left, top, width, height, text, font_size=12,
                 font_color=TEXT_DARK, bold=False, alignment=PP_ALIGN.LEFT,
                 font_name=FONT_TEXT):
    """Add a text box with Maersk fonts"""
    txBox = slide.shapes.add_textbox(left, top, width, height)
    tf = txBox.text_frame
    tf.word_wrap = True
    tf.auto_size = None

    p = tf.paragraphs[0]
    p.text = text
    p.font.size = Pt(font_size)
    p.font.color.rgb = font_color
    p.font.bold = bold
    p.font.name = font_name
    p.alignment = alignment

    return txBox


def add_headline(slide, left, top, width, height, text, font_size=24,
                 font_color=MAERSK_BLUE, alignment=PP_ALIGN.LEFT):
    """Add a headline with Maersk Headline font"""
    return add_text_box(slide, left, top, width, height, text,
                        font_size=font_size, font_color=font_color,
                        bold=False, alignment=alignment, font_name=FONT_HEADLINE)


def add_rounded_rect(slide, left, top, width, height, fill_color,
                     border_color=None, border_width=1, border_dash=False):
    """Add a rounded rectangle shape"""
    shape = slide.shapes.add_shape(
        MSO_SHAPE.ROUNDED_RECTANGLE,
        left, top, width, height
    )
    set_shape_fill(shape, fill_color)

    if border_color:
        shape.line.color.rgb = border_color
        shape.line.width = Pt(border_width)
        if border_dash:
            shape.line.dash_style = MSO_LINE_DASH_STYLE.DASH
    else:
        shape.line.fill.background()

    shape.adjustments[0] = 0.08
    return shape


def add_entrance_animation(slide, shape, delay_ms=0, duration_ms=500, effect="fade"):
    """Add entrance animation to a shape"""
    # Get or create timing node
    tree = slide._element

    # Find or create timing element
    timing = tree.find(qn('p:timing'))
    if timing is None:
        timing = etree.SubElement(tree, qn('p:timing'))

    tnLst = timing.find(qn('p:tnLst'))
    if tnLst is None:
        tnLst = etree.SubElement(timing, qn('p:tnLst'))

    # Create animation sequence
    par = tnLst.find(qn('p:par'))
    if par is None:
        par = etree.SubElement(tnLst, qn('p:par'))
        cTn = etree.SubElement(par, qn('p:cTn'), attrib={'id': '1', 'dur': 'indefinite', 'restart': 'never', 'nodeType': 'tmRoot'})
        childTnLst = etree.SubElement(cTn, qn('p:childTnLst'))
        seq = etree.SubElement(childTnLst, qn('p:seq'), attrib={'concurrent': '1', 'nextAc': 'seek'})
        seqCTn = etree.SubElement(seq, qn('p:cTn'), attrib={'id': '2', 'dur': 'indefinite', 'nodeType': 'mainSeq'})
        etree.SubElement(seqCTn, qn('p:childTnLst'))


def create_title_slide(prs):
    """Slide 1: Title Slide - Clean Maersk Blue/White"""
    slide = prs.slides.add_slide(prs.slide_layouts[6])

    # White background
    bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, prs.slide_height)
    set_shape_fill(bg, WHITE)
    bg.line.fill.background()

    # Top accent bar
    top_bar = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, Inches(0.06))
    set_shape_fill(top_bar, MAERSK_BLUE)
    top_bar.line.fill.background()

    # Maersk 7-point star
    star = slide.shapes.add_shape(MSO_SHAPE.STAR_7_POINT,
                                   Inches(4.5), Inches(1.2), Inches(1), Inches(1))
    set_shape_fill(star, MAERSK_CYAN)
    star.line.fill.background()

    # Main title
    add_headline(slide, Inches(0.5), Inches(2.5), Inches(9), Inches(0.8),
                 "Purchase Order Modernization",
                 font_size=44, font_color=MAERSK_BLUE, alignment=PP_ALIGN.CENTER)

    # Subtitle
    add_text_box(slide, Inches(0.5), Inches(3.4), Inches(9), Inches(0.5),
                 "From Legacy SQL to Cloud-Native Microservices",
                 font_size=20, font_color=MAERSK_CYAN,
                 alignment=PP_ALIGN.CENTER, font_name=FONT_HEADLINE)

    # Divider line
    line = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE,
                                   Inches(3.5), Inches(4.1), Inches(3), Inches(0.02))
    set_shape_fill(line, MAERSK_CYAN)
    line.line.fill.background()

    # Meta info
    add_text_box(slide, Inches(0.5), Inches(4.4), Inches(9), Inches(0.4),
                 "WMS Engineering  •  CTO Technical Review  •  Q4 2024",
                 font_size=12, font_color=TEXT_GRAY,
                 alignment=PP_ALIGN.CENTER)

    # Bottom accent bar
    bottom_bar = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE,
                                         0, prs.slide_height - Inches(0.08),
                                         prs.slide_width, Inches(0.08))
    set_shape_fill(bottom_bar, MAERSK_CYAN)
    bottom_bar.line.fill.background()

    return slide


def create_architecture_slide(prs):
    """Slide 2: 6-Layer Target Architecture - Blue/White theme"""
    slide = prs.slides.add_slide(prs.slide_layouts[6])

    # Light background
    bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, prs.slide_height)
    set_shape_fill(bg, LIGHT_GRAY)
    bg.line.fill.background()

    # White content card
    content = add_rounded_rect(slide, Inches(0.25), Inches(0.2),
                                Inches(9.5), Inches(5.2), WHITE)
    content.shadow.inherit = False

    # Title
    add_headline(slide, Inches(0.5), Inches(0.35), Inches(8), Inches(0.5),
                 "Target Architecture", font_size=26, font_color=MAERSK_BLUE)

    add_text_box(slide, Inches(0.5), Inches(0.75), Inches(8), Inches(0.3),
                 "6-Layer Microservice Architecture with 13 Maven Modules",
                 font_size=11, font_color=TEXT_GRAY)

    # Layer definitions - ALL BLUE THEME
    layers = [
        {
            "name": "ENTRY",
            "color": MAERSK_BLUE,
            "y": Inches(1.15),
            "modules": [
                ("REST API", "HTTP / Web / Mobile"),
                ("EDI Gateway", "X12 / EDIFACT"),
                ("RDT Devices", "RF Terminals"),
                ("Scheduler", "Cron Jobs"),
            ]
        },
        {
            "name": "API",
            "color": MAERSK_BLUE,
            "y": Inches(1.85),
            "modules": [
                ("po-api", "REST Controllers, DTOs, OpenAPI"),
                ("po-events", "Kafka Consumers, Event Handlers"),
            ]
        },
        {
            "name": "CORE",
            "color": MAERSK_LIGHT_BLUE,
            "y": Inches(2.55),
            "modules": [
                ("po-core", "Business Services, Orchestration"),
                ("po-domain", "Entities, Value Objects, Events"),
                ("po-repository", "Data Access, JDBC, jOOQ"),
            ]
        },
        {
            "name": "SERVICES",
            "color": MAERSK_CYAN,
            "y": Inches(3.25),
            "modules": [
                ("po-rules", "Drools 8, Decision Tables"),
                ("po-workflow", "Temporal, Saga Pattern"),
                ("po-plugin", "Java SPI Extensions"),
                ("po-config", "YAML, Feature Flags"),
            ]
        },
        {
            "name": "INFRA",
            "color": MAERSK_LIGHT_BLUE,
            "y": Inches(3.95),
            "modules": [
                ("po-integration", "WCS, Label Printers, 3PL"),
                ("po-v2-adapter", "Legacy SP Bridge"),
                ("po-common", "Utils, Constants"),
            ]
        },
        {
            "name": "TESTING",
            "color": MAERSK_BLUE,
            "y": Inches(4.65),
            "modules": [
                ("po-test", "JUnit 5, Mockito, Testcontainers"),
                ("po-e2e", "Karate E2E, SP Parity Tests"),
            ]
        },
    ]

    badge_x = Inches(0.45)
    modules_start_x = Inches(1.65)

    for layer in layers:
        # Layer badge
        badge = add_rounded_rect(slide, badge_x, layer["y"],
                                  Inches(1.0), Inches(0.55), layer["color"])
        badge.adjustments[0] = 0.12

        add_text_box(slide, badge_x, layer["y"] + Inches(0.15),
                     Inches(1.0), Inches(0.3),
                     layer["name"], font_size=10, font_color=WHITE,
                     bold=True, alignment=PP_ALIGN.CENTER, font_name=FONT_HEADLINE)

        # Module cards
        num_modules = len(layer["modules"])
        if num_modules == 4:
            card_width = Inches(1.9)
            gap = Inches(0.08)
        elif num_modules == 3:
            card_width = Inches(2.5)
            gap = Inches(0.1)
        else:
            card_width = Inches(3.8)
            gap = Inches(0.12)

        for i, (name, desc) in enumerate(layer["modules"]):
            x = modules_start_x + i * (card_width + gap)

            # Card with subtle border
            card = add_rounded_rect(slide, x, layer["y"],
                                     card_width, Inches(0.55), WHITE,
                                     border_color=BORDER_LIGHT, border_width=1)

            # Module name
            add_text_box(slide, x + Inches(0.08), layer["y"] + Inches(0.08),
                         card_width - Inches(0.12), Inches(0.22),
                         name, font_size=11, font_color=MAERSK_CYAN,
                         font_name=FONT_HEADLINE)

            # Description
            add_text_box(slide, x + Inches(0.08), layer["y"] + Inches(0.3),
                         card_width - Inches(0.12), Inches(0.22),
                         desc, font_size=8, font_color=TEXT_GRAY)

    # Down arrows between layers
    arrow_x = Inches(0.82)
    arrow_positions = [Inches(1.72), Inches(2.42), Inches(3.12), Inches(3.82), Inches(4.52)]
    for ay in arrow_positions:
        add_text_box(slide, arrow_x, ay, Inches(0.4), Inches(0.15),
                     "↓", font_size=11, font_color=MAERSK_CYAN,
                     alignment=PP_ALIGN.CENTER)

    return slide


def create_stats_slide(prs):
    """Slide 3: Project Scale - Blue/White theme"""
    slide = prs.slides.add_slide(prs.slide_layouts[6])

    # Light background
    bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, prs.slide_height)
    set_shape_fill(bg, LIGHT_GRAY)
    bg.line.fill.background()

    # White content card
    content = add_rounded_rect(slide, Inches(0.25), Inches(0.2),
                                Inches(9.5), Inches(5.2), WHITE)

    # Title
    add_headline(slide, Inches(0.5), Inches(0.35), Inches(8), Inches(0.5),
                 "Project Scale", font_size=26, font_color=MAERSK_BLUE)

    add_text_box(slide, Inches(0.5), Inches(0.75), Inches(8), Inches(0.3),
                 "Legacy system complexity being modernized",
                 font_size=11, font_color=TEXT_GRAY)

    # Stats - Row 1
    stats_row1 = [
        ("115", "Stored Procedures"),
        ("37,300+", "Lines of SQL"),
        ("13", "Maven Modules"),
        ("28,453", "Living Docs"),
    ]

    # Stats - Row 2
    stats_row2 = [
        ("140", "DRL Rules"),
        ("145", "Config Keys"),
        ("65+", "Plugins"),
        ("234K+", "Dependency Edges"),
    ]

    card_width = Inches(2.15)
    card_height = Inches(1.5)
    start_x = Inches(0.5)
    gap = Inches(0.15)

    for row_idx, stats in enumerate([stats_row1, stats_row2]):
        y = Inches(1.2) + row_idx * (card_height + Inches(0.2))
        for col_idx, (value, label) in enumerate(stats):
            x = start_x + col_idx * (card_width + gap)

            # Card
            card = add_rounded_rect(slide, x, y, card_width, card_height, WHITE,
                                     border_color=BORDER_LIGHT)

            # Top accent
            accent = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE,
                                            x, y, card_width, Inches(0.05))
            set_shape_fill(accent, MAERSK_CYAN)
            accent.line.fill.background()

            # Value
            add_headline(slide, x, y + Inches(0.35), card_width, Inches(0.6),
                         value, font_size=36, font_color=MAERSK_BLUE,
                         alignment=PP_ALIGN.CENTER)

            # Label
            add_text_box(slide, x, y + Inches(1.05), card_width, Inches(0.35),
                         label, font_size=11, font_color=TEXT_GRAY,
                         alignment=PP_ALIGN.CENTER)

    return slide


def create_journey_slide(prs):
    """Slide 4: Three-Phase Journey - Blue/White theme"""
    slide = prs.slides.add_slide(prs.slide_layouts[6])

    # Light background
    bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, prs.slide_height)
    set_shape_fill(bg, LIGHT_GRAY)
    bg.line.fill.background()

    # White content card
    content = add_rounded_rect(slide, Inches(0.25), Inches(0.2),
                                Inches(9.5), Inches(5.2), WHITE)

    # Title
    add_headline(slide, Inches(0.5), Inches(0.35), Inches(8), Inches(0.5),
                 "Modernization Journey", font_size=26, font_color=MAERSK_BLUE)

    phases = [
        {
            "number": "1",
            "title": "Foundation",
            "subtitle": "UNDERSTAND",
            "items": [
                "Knowledge Extraction (Claude AI)",
                "Knowledge Graph (234K+ edges)",
                "Living Documentation",
                "DI & Call Graph Mapping",
            ],
            "metric": ("28,453", "Docs Generated")
        },
        {
            "number": "2",
            "title": "Migration",
            "subtitle": "MODERNIZE",
            "items": [
                "Pattern Analysis",
                "Variation Resolver (V0/V2)",
                "Drools Rules Engine",
                "Plugin Architecture",
            ],
            "metric": ("140", "Business Rules")
        },
        {
            "number": "3",
            "title": "Validation",
            "subtitle": "TEST & VERIFY",
            "items": [
                "SP Parity Testing",
                "Karate E2E Framework",
                "Data Reconciliation",
                "AI Test Generation",
            ],
            "metric": ("100%", "Parity Target")
        },
    ]

    phase_width = Inches(2.95)
    phase_height = Inches(4.0)
    start_x = Inches(0.4)
    phase_y = Inches(0.95)
    gap = Inches(0.15)

    for i, phase in enumerate(phases):
        x = start_x + i * (phase_width + gap)

        # Phase card
        card = add_rounded_rect(slide, x, phase_y, phase_width, phase_height, WHITE,
                                border_color=BORDER_LIGHT)

        # Top accent bar
        bar = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE,
                                      x, phase_y, phase_width, Inches(0.05))
        set_shape_fill(bar, MAERSK_CYAN)
        bar.line.fill.background()

        # Phase number circle
        circle = slide.shapes.add_shape(MSO_SHAPE.OVAL,
                                        x + Inches(0.15), phase_y + Inches(0.2),
                                        Inches(0.45), Inches(0.45))
        set_shape_fill(circle, MAERSK_BLUE)
        circle.line.fill.background()

        add_text_box(slide, x + Inches(0.15), phase_y + Inches(0.28),
                     Inches(0.45), Inches(0.3),
                     phase["number"], font_size=18, font_color=WHITE,
                     bold=True, alignment=PP_ALIGN.CENTER, font_name=FONT_HEADLINE)

        # Phase title
        add_headline(slide, x + Inches(0.7), phase_y + Inches(0.22),
                     Inches(2.1), Inches(0.35),
                     phase["title"], font_size=18, font_color=MAERSK_BLUE)

        # Phase subtitle
        add_text_box(slide, x + Inches(0.7), phase_y + Inches(0.52),
                     Inches(2.1), Inches(0.22),
                     phase["subtitle"], font_size=9, font_color=MAERSK_CYAN,
                     bold=True, font_name=FONT_HEADLINE)

        # Items
        for j, item in enumerate(phase["items"]):
            item_y = phase_y + Inches(0.95) + j * Inches(0.58)

            # Item background
            item_bg = add_rounded_rect(slide, x + Inches(0.1), item_y,
                                       Inches(2.75), Inches(0.48), LIGHT_GRAY)
            item_bg.adjustments[0] = 0.15

            # Left accent bar
            accent = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE,
                                            x + Inches(0.1), item_y,
                                            Inches(0.04), Inches(0.48))
            set_shape_fill(accent, MAERSK_CYAN)
            accent.line.fill.background()

            add_text_box(slide, x + Inches(0.22), item_y + Inches(0.12),
                         Inches(2.55), Inches(0.28),
                         item, font_size=10, font_color=TEXT_DARK)

        # Metric at bottom
        metric_y = phase_y + phase_height - Inches(0.65)
        metric_bg = add_rounded_rect(slide, x + Inches(0.1), metric_y,
                                     Inches(2.75), Inches(0.5), MAERSK_BLUE)

        add_headline(slide, x + Inches(0.15), metric_y + Inches(0.08),
                     Inches(1.2), Inches(0.35),
                     phase["metric"][0], font_size=20, font_color=WHITE,
                     alignment=PP_ALIGN.CENTER)

        add_text_box(slide, x + Inches(1.35), metric_y + Inches(0.15),
                     Inches(1.4), Inches(0.25),
                     phase["metric"][1], font_size=9, font_color=WHITE)

    # Arrows between phases
    for i in range(2):
        arrow_x = start_x + (i + 1) * phase_width + i * gap + Inches(0.02)
        arrow = slide.shapes.add_shape(MSO_SHAPE.RIGHT_ARROW,
                                       arrow_x, phase_y + Inches(0.38),
                                       Inches(0.12), Inches(0.18))
        set_shape_fill(arrow, MAERSK_CYAN)
        arrow.line.fill.background()

    return slide


def create_workflow_slide(prs):
    """Slide 5: Workflow Pipeline Details - Blue/White theme"""
    slide = prs.slides.add_slide(prs.slide_layouts[6])

    # Light background
    bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, prs.slide_height)
    set_shape_fill(bg, LIGHT_GRAY)
    bg.line.fill.background()

    # White content card
    content = add_rounded_rect(slide, Inches(0.25), Inches(0.2),
                                Inches(9.5), Inches(5.2), WHITE)

    # Title
    add_headline(slide, Inches(0.5), Inches(0.3), Inches(8), Inches(0.45),
                 "Workflow Pipeline", font_size=24, font_color=MAERSK_BLUE)

    add_text_box(slide, Inches(0.5), Inches(0.68), Inches(8), Inches(0.25),
                 "9-Step Processing Pipeline with Saga Orchestration",
                 font_size=10, font_color=TEXT_GRAY)

    # =========================================
    # TOP SECTION: 9-Step Horizontal Pipeline
    # =========================================
    pipeline_steps = [
        ("1", "Resolve\nContext", "V0/V2, Region"),
        ("2", "Pre-\nPlugins", "Client Hooks"),
        ("3", "Validate", "Business Rules"),
        ("4", "Map", "PO → Receipt"),
        ("5", "Lottables", "Rules Engine"),
        ("6", "Persist", "Create Receipt"),
        ("7", "Inventory", "Reserve Stock"),
        ("8", "Legacy\nSync", "Dual-Write"),
        ("9", "Notify", "Events"),
    ]

    step_width = Inches(0.95)
    step_height = Inches(0.85)
    pipeline_x = Inches(0.45)
    pipeline_y = Inches(1.0)
    gap = Inches(0.08)

    for i, (num, title, desc) in enumerate(pipeline_steps):
        x = pipeline_x + i * (step_width + gap)

        # Step card
        card = add_rounded_rect(slide, x, pipeline_y, step_width, step_height, WHITE,
                                border_color=MAERSK_CYAN, border_width=1)
        card.adjustments[0] = 0.1

        # Step number circle
        circle = slide.shapes.add_shape(MSO_SHAPE.OVAL,
                                        x + Inches(0.35), pipeline_y - Inches(0.12),
                                        Inches(0.25), Inches(0.25))
        set_shape_fill(circle, MAERSK_BLUE)
        circle.line.fill.background()

        add_text_box(slide, x + Inches(0.35), pipeline_y - Inches(0.08),
                     Inches(0.25), Inches(0.2),
                     num, font_size=9, font_color=WHITE,
                     bold=True, alignment=PP_ALIGN.CENTER, font_name=FONT_HEADLINE)

        # Title
        add_text_box(slide, x + Inches(0.05), pipeline_y + Inches(0.18),
                     step_width - Inches(0.1), Inches(0.4),
                     title, font_size=9, font_color=MAERSK_BLUE,
                     bold=True, alignment=PP_ALIGN.CENTER, font_name=FONT_HEADLINE)

        # Description
        add_text_box(slide, x + Inches(0.05), pipeline_y + Inches(0.58),
                     step_width - Inches(0.1), Inches(0.22),
                     desc, font_size=7, font_color=TEXT_GRAY,
                     alignment=PP_ALIGN.CENTER)

        # Arrow between steps (except last)
        if i < len(pipeline_steps) - 1:
            arrow_x = x + step_width + Inches(0.01)
            arrow = slide.shapes.add_shape(MSO_SHAPE.RIGHT_ARROW,
                                           arrow_x, pipeline_y + Inches(0.35),
                                           Inches(0.06), Inches(0.15))
            set_shape_fill(arrow, MAERSK_CYAN)
            arrow.line.fill.background()

    # =========================================
    # BOTTOM SECTION: Architecture Flow
    # =========================================
    section_title_y = Inches(2.0)
    add_headline(slide, Inches(0.5), section_title_y, Inches(4), Inches(0.3),
                 "Architecture Flow", font_size=14, font_color=MAERSK_BLUE)

    # Flow components (vertical layout with horizontal activities)
    flow_y = Inches(2.35)

    # Row 1: REST API
    api_box = add_rounded_rect(slide, Inches(3.5), flow_y,
                                Inches(3), Inches(0.45), MAERSK_BLUE)
    add_text_box(slide, Inches(3.5), flow_y + Inches(0.05),
                 Inches(3), Inches(0.2),
                 "REST API", font_size=11, font_color=WHITE,
                 bold=True, alignment=PP_ALIGN.CENTER, font_name=FONT_HEADLINE)
    add_text_box(slide, Inches(3.5), flow_y + Inches(0.25),
                 Inches(3), Inches(0.18),
                 "po-api • Controllers • DTOs", font_size=8, font_color=MAERSK_CYAN,
                 alignment=PP_ALIGN.CENTER)

    # Arrow down
    flow_y += Inches(0.5)
    add_text_box(slide, Inches(4.85), flow_y, Inches(0.3), Inches(0.18),
                 "↓", font_size=12, font_color=MAERSK_CYAN, alignment=PP_ALIGN.CENTER)

    # Row 2: Variation Resolver
    flow_y += Inches(0.2)
    resolver_box = add_rounded_rect(slide, Inches(2.5), flow_y,
                                     Inches(5), Inches(0.45), MAERSK_LIGHT_BLUE)
    add_text_box(slide, Inches(2.5), flow_y + Inches(0.05),
                 Inches(5), Inches(0.2),
                 "Variation Resolver", font_size=11, font_color=WHITE,
                 bold=True, alignment=PP_ALIGN.CENTER, font_name=FONT_HEADLINE)
    add_text_box(slide, Inches(2.5), flow_y + Inches(0.25),
                 Inches(5), Inches(0.18),
                 "V0/V2 Detection  •  Region Context  •  Client Resolution", font_size=8, font_color=WHITE,
                 alignment=PP_ALIGN.CENTER)

    # Arrow down
    flow_y += Inches(0.5)
    add_text_box(slide, Inches(4.85), flow_y, Inches(0.3), Inches(0.18),
                 "↓", font_size=12, font_color=MAERSK_CYAN, alignment=PP_ALIGN.CENTER)

    # Row 3: Temporal Workflow
    flow_y += Inches(0.2)
    workflow_box = add_rounded_rect(slide, Inches(2.0), flow_y,
                                     Inches(6), Inches(0.45), MAERSK_BLUE)
    add_text_box(slide, Inches(2.0), flow_y + Inches(0.05),
                 Inches(6), Inches(0.2),
                 "Temporal Workflow", font_size=11, font_color=WHITE,
                 bold=True, alignment=PP_ALIGN.CENTER, font_name=FONT_HEADLINE)
    add_text_box(slide, Inches(2.0), flow_y + Inches(0.25),
                 Inches(6), Inches(0.18),
                 "Saga Orchestration  •  Compensation  •  Retry Logic", font_size=8, font_color=MAERSK_CYAN,
                 alignment=PP_ALIGN.CENTER)

    # Arrow down (split to 4)
    flow_y += Inches(0.5)
    add_text_box(slide, Inches(4.85), flow_y, Inches(0.3), Inches(0.18),
                 "↓", font_size=12, font_color=MAERSK_CYAN, alignment=PP_ALIGN.CENTER)

    # Row 4: Four Activities (horizontal)
    flow_y += Inches(0.22)
    activities = [
        ("Validation", "po-rules"),
        ("Mapping", "po-domain"),
        ("Persistence", "po-repository"),
        ("Plugins", "po-plugin"),
    ]

    activity_width = Inches(2.2)
    activity_start_x = Inches(0.55)
    activity_gap = Inches(0.12)

    for i, (title, module) in enumerate(activities):
        x = activity_start_x + i * (activity_width + activity_gap)

        activity_box = add_rounded_rect(slide, x, flow_y,
                                         activity_width, Inches(0.42), MAERSK_CYAN)
        add_text_box(slide, x, flow_y + Inches(0.05),
                     activity_width, Inches(0.18),
                     title, font_size=10, font_color=WHITE,
                     bold=True, alignment=PP_ALIGN.CENTER, font_name=FONT_HEADLINE)
        add_text_box(slide, x, flow_y + Inches(0.22),
                     activity_width, Inches(0.18),
                     module, font_size=8, font_color=WHITE,
                     alignment=PP_ALIGN.CENTER)

    # Arrow down
    flow_y += Inches(0.47)
    add_text_box(slide, Inches(4.85), flow_y, Inches(0.3), Inches(0.18),
                 "↓", font_size=12, font_color=MAERSK_CYAN, alignment=PP_ALIGN.CENTER)

    # Row 5: Legacy Bridge
    flow_y += Inches(0.2)
    bridge_box = add_rounded_rect(slide, Inches(2.5), flow_y,
                                   Inches(5), Inches(0.42), MAERSK_LIGHT_BLUE)
    add_text_box(slide, Inches(2.5), flow_y + Inches(0.05),
                 Inches(5), Inches(0.18),
                 "Legacy Bridge", font_size=10, font_color=WHITE,
                 bold=True, alignment=PP_ALIGN.CENTER, font_name=FONT_HEADLINE)
    add_text_box(slide, Inches(2.5), flow_y + Inches(0.22),
                 Inches(5), Inches(0.18),
                 "po-v2-adapter  •  Dual-Write Support  •  Parity Validation", font_size=8, font_color=WHITE,
                 alignment=PP_ALIGN.CENTER)

    # Arrow down (split to 2)
    flow_y += Inches(0.47)
    add_text_box(slide, Inches(3.35), flow_y, Inches(0.3), Inches(0.18),
                 "↓", font_size=12, font_color=MAERSK_CYAN, alignment=PP_ALIGN.CENTER)
    add_text_box(slide, Inches(6.35), flow_y, Inches(0.3), Inches(0.18),
                 "↓", font_size=12, font_color=MAERSK_CYAN, alignment=PP_ALIGN.CENTER)

    # Row 6: Two Databases
    flow_y += Inches(0.2)
    db_width = Inches(3.5)

    # V0 Database
    v0_box = add_rounded_rect(slide, Inches(1.0), flow_y,
                               db_width, Inches(0.38), MAERSK_BLUE)
    add_text_box(slide, Inches(1.0), flow_y + Inches(0.08),
                 db_width, Inches(0.22),
                 "V0 Database (Legacy Unified)", font_size=9, font_color=WHITE,
                 bold=True, alignment=PP_ALIGN.CENTER, font_name=FONT_HEADLINE)

    # V2 Database
    v2_box = add_rounded_rect(slide, Inches(5.5), flow_y,
                               db_width, Inches(0.38), MAERSK_BLUE)
    add_text_box(slide, Inches(5.5), flow_y + Inches(0.08),
                 db_width, Inches(0.22),
                 "V2 Database (Current)", font_size=9, font_color=WHITE,
                 bold=True, alignment=PP_ALIGN.CENTER, font_name=FONT_HEADLINE)

    return slide


def create_parity_slide(prs):
    """Slide 6: SP Parity Testing Flow - Blue/White theme"""
    slide = prs.slides.add_slide(prs.slide_layouts[6])

    # Light background
    bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, prs.slide_height)
    set_shape_fill(bg, LIGHT_GRAY)
    bg.line.fill.background()

    # White content card
    content = add_rounded_rect(slide, Inches(0.25), Inches(0.2),
                                Inches(9.5), Inches(5.2), WHITE)

    # Title
    add_headline(slide, Inches(0.5), Inches(0.35), Inches(6), Inches(0.5),
                 "SP Parity Testing Flow", font_size=26, font_color=MAERSK_BLUE)

    # Critical badge
    badge = add_rounded_rect(slide, Inches(7.0), Inches(0.35),
                              Inches(1.5), Inches(0.35), MAERSK_BLUE)
    add_text_box(slide, Inches(7.0), Inches(0.4), Inches(1.5), Inches(0.3),
                 "CRITICAL PHASE", font_size=9, font_color=WHITE,
                 bold=True, alignment=PP_ALIGN.CENTER, font_name=FONT_HEADLINE)

    # Flow diagram
    flow_steps = [
        ("Request", "API Call"),
        ("Router", "Feature Flag"),
        ("Dual Execute", "Java + SP"),
        ("Compare", "Field-by-Field"),
        ("✓ Parity", "Verified"),
    ]

    step_width = Inches(1.6)
    step_height = Inches(0.95)
    flow_x = Inches(0.5)
    flow_y = Inches(1.0)

    for i, (title, subtitle) in enumerate(flow_steps):
        x = flow_x + i * (step_width + Inches(0.12))

        # Step card
        is_final = (i == 4)
        card = add_rounded_rect(slide, x, flow_y, step_width, step_height,
                                MAERSK_BLUE if is_final else LIGHT_GRAY,
                                border_color=MAERSK_CYAN if is_final else BORDER_LIGHT)

        add_headline(slide, x, flow_y + Inches(0.2), step_width, Inches(0.35),
                     title, font_size=13,
                     font_color=WHITE if is_final else MAERSK_BLUE,
                     alignment=PP_ALIGN.CENTER)

        add_text_box(slide, x, flow_y + Inches(0.55), step_width, Inches(0.25),
                     subtitle, font_size=9,
                     font_color=MAERSK_CYAN if is_final else TEXT_GRAY,
                     alignment=PP_ALIGN.CENTER)

        # Arrow
        if i < len(flow_steps) - 1:
            arrow = slide.shapes.add_shape(MSO_SHAPE.RIGHT_ARROW,
                                           x + step_width + Inches(0.02),
                                           flow_y + Inches(0.38),
                                           Inches(0.1), Inches(0.18))
            set_shape_fill(arrow, MAERSK_CYAN)
            arrow.line.fill.background()

    # Detail cards
    details = [
        ("Dual-Write Validation", "Execute both Java service and legacy SP simultaneously, compare results in real-time"),
        ("Field-by-Field Compare", "Deep comparison of every output field with configurable tolerance thresholds"),
        ("Shadow Mode", "Run new code alongside legacy without affecting production data or user experience"),
        ("Reconciliation Engine", "Automated drift detection with anomaly alerting and detailed reporting"),
    ]

    card_width = Inches(4.4)
    card_height = Inches(0.75)
    detail_y = Inches(2.15)

    for i, (title, desc) in enumerate(details):
        row = i // 2
        col = i % 2
        x = Inches(0.5) + col * (card_width + Inches(0.15))
        y = detail_y + row * (card_height + Inches(0.12))

        card = add_rounded_rect(slide, x, y, card_width, card_height, WHITE,
                                border_color=BORDER_LIGHT)

        # Left accent
        accent = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE,
                                        x, y, Inches(0.05), card_height)
        set_shape_fill(accent, MAERSK_CYAN)
        accent.line.fill.background()

        add_headline(slide, x + Inches(0.15), y + Inches(0.1),
                     card_width - Inches(0.2), Inches(0.25),
                     title, font_size=12, font_color=MAERSK_BLUE)

        add_text_box(slide, x + Inches(0.15), y + Inches(0.4),
                     card_width - Inches(0.2), Inches(0.3),
                     desc, font_size=9, font_color=TEXT_GRAY)

    # Bottom metrics
    metrics = [("100%", "Parity Target"), ("0", "Regressions"),
               ("<50ms", "Reconcile Time"), ("115", "SPs Covered")]

    metric_width = Inches(2.15)
    metric_y = Inches(3.95)

    for i, (value, label) in enumerate(metrics):
        x = Inches(0.5) + i * (metric_width + Inches(0.12))

        metric_bg = add_rounded_rect(slide, x, metric_y,
                                      metric_width, Inches(0.75), LIGHT_GRAY)

        add_headline(slide, x, metric_y + Inches(0.1), metric_width, Inches(0.35),
                     value, font_size=24, font_color=MAERSK_BLUE,
                     alignment=PP_ALIGN.CENTER)

        add_text_box(slide, x, metric_y + Inches(0.48), metric_width, Inches(0.22),
                     label, font_size=9, font_color=TEXT_GRAY,
                     alignment=PP_ALIGN.CENTER)

    return slide


def create_summary_slide(prs):
    """Slide 6: Summary - Blue/White theme"""
    slide = prs.slides.add_slide(prs.slide_layouts[6])

    # Light background
    bg = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE, 0, 0, prs.slide_width, prs.slide_height)
    set_shape_fill(bg, LIGHT_GRAY)
    bg.line.fill.background()

    # White content card
    content = add_rounded_rect(slide, Inches(0.25), Inches(0.2),
                                Inches(9.5), Inches(5.2), WHITE)

    # Title
    add_headline(slide, Inches(0.5), Inches(0.35), Inches(8), Inches(0.5),
                 "Summary", font_size=26, font_color=MAERSK_BLUE)

    add_text_box(slide, Inches(0.5), Inches(0.75), Inches(8), Inches(0.3),
                 "PO Modernization delivers a future-ready architecture",
                 font_size=11, font_color=TEXT_GRAY)

    # Key metrics
    metrics = [
        ("115 → 13", "SPs → Modules"),
        ("100%", "Parity Tested"),
        ("0", "Regressions"),
        ("∞", "Scalability"),
    ]

    card_width = Inches(2.15)
    card_height = Inches(1.15)
    start_x = Inches(0.5)

    for i, (value, label) in enumerate(metrics):
        x = start_x + i * (card_width + Inches(0.12))

        card = add_rounded_rect(slide, x, Inches(1.15), card_width, card_height, WHITE,
                                border_color=BORDER_LIGHT)

        # Top accent
        accent = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE,
                                        x, Inches(1.15), card_width, Inches(0.05))
        set_shape_fill(accent, MAERSK_CYAN)
        accent.line.fill.background()

        add_headline(slide, x, Inches(1.35), card_width, Inches(0.5),
                     value, font_size=28, font_color=MAERSK_BLUE,
                     alignment=PP_ALIGN.CENTER)

        add_text_box(slide, x, Inches(1.88), card_width, Inches(0.3),
                     label, font_size=10, font_color=TEXT_GRAY,
                     alignment=PP_ALIGN.CENTER)

    # Key achievements
    achievements = [
        ("AI-Powered Knowledge Base", "28,453 living documents, 234K+ dependency edges, Claude-assisted code generation"),
        ("Production Ready", "Dual-write validation, shadow mode testing, gradual rollout with feature flags"),
    ]

    for i, (title, desc) in enumerate(achievements):
        y = Inches(2.55) + i * Inches(1.0)

        card = add_rounded_rect(slide, Inches(0.5), y,
                                Inches(8.5), Inches(0.85), WHITE,
                                border_color=BORDER_LIGHT)

        # Checkmark circle
        circle = slide.shapes.add_shape(MSO_SHAPE.OVAL,
                                        Inches(0.65), y + Inches(0.18),
                                        Inches(0.5), Inches(0.5))
        set_shape_fill(circle, MAERSK_CYAN)
        circle.line.fill.background()

        add_text_box(slide, Inches(0.65), y + Inches(0.25),
                     Inches(0.5), Inches(0.35),
                     "✓", font_size=20, font_color=WHITE,
                     bold=True, alignment=PP_ALIGN.CENTER)

        add_headline(slide, Inches(1.3), y + Inches(0.12),
                     Inches(7.5), Inches(0.3),
                     title, font_size=15, font_color=MAERSK_BLUE)

        add_text_box(slide, Inches(1.3), y + Inches(0.48),
                     Inches(7.5), Inches(0.32),
                     desc, font_size=10, font_color=TEXT_GRAY)

    # Footer
    add_text_box(slide, Inches(0.5), Inches(4.85), Inches(4), Inches(0.25),
                 "PO Modernization  •  Maersk WMS",
                 font_size=9, font_color=TEXT_GRAY)

    add_text_box(slide, Inches(5), Inches(4.85), Inches(4.5), Inches(0.25),
                 "© 2024 A.P. Moller - Maersk",
                 font_size=9, font_color=TEXT_GRAY, alignment=PP_ALIGN.RIGHT)

    # Bottom accent
    bottom = slide.shapes.add_shape(MSO_SHAPE.RECTANGLE,
                                     Inches(0.25), Inches(5.35),
                                     Inches(9.5), Inches(0.04))
    set_shape_fill(bottom, MAERSK_CYAN)
    bottom.line.fill.background()

    return slide


def main():
    """Generate the PowerPoint presentation"""
    prs = Presentation()
    prs.slide_width = Inches(10)
    prs.slide_height = Inches(5.625)

    print("=" * 60)
    print("  PO Modernization CTO Presentation Generator")
    print("  Maersk Design System - Blue/White Theme")
    print("=" * 60)
    print()

    print("Creating slides with Maersk fonts...")
    print()

    print("  [1/7] Title slide...")
    create_title_slide(prs)

    print("  [2/7] Target Architecture (6-layer)...")
    create_architecture_slide(prs)

    print("  [3/7] Project Scale (metrics)...")
    create_stats_slide(prs)

    print("  [4/7] Modernization Journey (3 phases)...")
    create_journey_slide(prs)

    print("  [5/7] Workflow Pipeline (9-step + Architecture)...")
    create_workflow_slide(prs)

    print("  [6/7] SP Parity Testing Flow...")
    create_parity_slide(prs)

    print("  [7/7] Summary...")
    create_summary_slide(prs)

    output_path = os.path.join(os.path.dirname(__file__), "CTO_PRESENTATION_MAERSK.pptx")
    prs.save(output_path)

    print()
    print("=" * 60)
    print(f"  ✓ Saved: {output_path}")
    print("=" * 60)
    print()
    print("Design System Applied:")
    print("  • Fonts: Maersk Headline, Maersk Text")
    print("  • Colors: Blue (#00243D), Cyan (#42B0D5), White")
    print("  • Cards: White with subtle borders")
    print("  • Accents: Cyan top bars and left borders")
    print()
    print("To add animations in PowerPoint:")
    print("  1. Open the .pptx file")
    print("  2. Select all shapes on a slide (Ctrl+A)")
    print("  3. Animations tab → Add Animation → Fade")
    print("  4. Animation Pane → Set timing (0.3s delay between items)")
    print()


if __name__ == "__main__":
    main()
