
include ../resources/make.param

TUTORIAL_MAIN=main.txt
SOURCE_DEPS= \
		$(TUTORIAL_MAIN) \
		starting.txt \
		first_map.txt second_map.txt publish.txt \
		splitter.txt custom.txt \
		$(XSL) 

build: html

all: html pdf epub

html: tutorial.html
pdf: tutorial.pdf
epub: tutorial.epub

tutorial.html: $(SOURCE_DEPS)
	$(ASCIIDOC) $(HTML_ATTRS) -o tutorial.html -a icons -a iconsdir=$(ICONSDIR) -b html5 $(TUTORIAL_MAIN)

tutorial.pdf: $(SOURCE_DEPS)
	$(A2X) --xsl-file=$(XSL) --fop -a doctype=article $(PDF_OPTS) $(TUTORIAL_MAIN)
	mv main.pdf tutorial.pdf

tutorial.epub: $(SOURCE_DEPS)
	mkdir -p build
	cp -ru $(RES)/common-images build
	$(A2X) --format epub --icons --icons-dir=build/common-images/icons $(TUTORIAL_MAIN)


install: pdf
	cp -u tutorial.pdf $(DEST)

clean:
	rm -f tutorial.{pdf,epub,html}
	rm -rf build
