#!/usr/bin/env perl
#
# Create a transliteration table for the TableTransliterator.
# It just gets the values from the Test::Unidecode module of perl.
#
# Based on a script by Ævar Arnfjörð Bjarmason
#

use feature ':5.10';
use strict;
use warnings;

use Unicode::UCD 'charinfo';
use Text::Unidecode;
use Data::Dump 'dump';

#
# Based on a script by Ævar Arnfjörð Bjarmason
#
my $row = $ARGV[0];
#if (!$row) {
#	exit 1;
#}

sub new_trans {
    my $char_val = shift;

	my $t;
	if ($t = unidecode($char_val) and $t !~ /[?].*/) {
		return $t;
	}

    return "?";
}

binmode STDOUT, ":utf8";

say <<EOF;
#
# A look up table to transliterate to ascii.
# Created with the Text::Unidecode module of perl
#
EOF

for (my $i = 0; $i < 256; $i++) {
	my $trans;
	
	my $c = $row * 256 + $i;
	$trans = new_trans(chr $c);

	my $char_name = sprintf("U+%02x%02x", $row, $i);
	if ($trans) {
		say sprintf("%s %-12.12s # Character %c", $char_name,
			$trans, $c);
	} else {
		say sprintf("%s %-12.12s # Character %c", $char_name, "?",
			$c);
	}
}
