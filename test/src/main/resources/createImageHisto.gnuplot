#
set key invert reverse Left outside
set key autotitle columnheader
set auto x
unset xtics
set xtics nomirror rotate by 90 offset 0,-1.3
set style data histogram
set style histogram rowstacked
set style fill pattern
set boxwidth 0.75
set size 1.2,0.8

set style line 1 pt 1 lt 1 lw 1 lc 0
set style line 2 pt 2 lt 2 lw 1 lc 0 
set style line 3 pt 8 lt 3 lw 1 lc 0
set style line 4 pt 4 lt 6 lw 1 lc 0 
set style line 5 pt 5 lt 1 lw 1 lc 0


set term post eps enhanced solid "Helvetica" 22
set out "graph.eps"
set xlabel '<xlabel>'
set ylabel '<ylabel>'

#<extra>
#<extra1>

#plot 'gnuplot.values' using 2:xtic(1) ls 1, '' using 3 ls 1, '' using 4 ls 1, '' using 5 ls 1, '' using 6 ls 1, '' using 7
plot 'gnuplot.values' using 2:xtic(1) title "<title1>" ls 1 \
		#<do2>, '' using 3 title "<title2>" ls 2 \
		#<do3>, '' using 4 title "<title3>" ls 3 \
		#<do4>, '' using 5 title "<title4>" ls 4 \
		#<do5>, '' using 6 title "<title5>" ls 5 \
		#<do6>, '' using 7 title "<title6>" ls 6 \
		#<do7>, '' using 8 title "<title7>" ls 7

