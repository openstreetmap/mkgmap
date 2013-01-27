
include ../resources/make.param


SOURCES= about.txt creating.txt design.txt files.txt rules.txt rules-filters.txt \
	style-manual.txt $(XSL) 
STYLE_MAIN=style-manual.txt
TARGET_PDF=style-manual.pdf

build: html

all: html pdf epub

html: style-manual.html
pdf: style-manual.pdf
epub: style-manual.epub

style-manual.html: $(SOURCES)
	$(ASCIIDOC) $(HTML_ATTRS) -a icons -a iconsdir=$(ICONSDIR) -b html5 $(STYLE_MAIN)

$(TARGET_PDF): $(SOURCES)
	$(A2X) --xsl-file=$(XSL) --fop $(PDF_OPTS) $(STYLE_MAIN)

style-manual.epub: $(SOURCES)
	mkdir -p build
	cp -ru $(RES)/common-images build
	$(A2X) --format epub --icons --icons-dir=build/common-images/icons $(STYLE_MAIN)


install: $(DEST)/$(TARGET_PDF)
	cp -u $(TARGET_PDF) $(DEST)

$(DEST)/$(TARGET_PDF): pdf

clean:
	rm -f style-manual.{pdf,epub,html}
	rm -rf build
