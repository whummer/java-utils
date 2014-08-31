#regression regrFunc(x) = k1 * x + d1              # define the function to be fit
#regression k1 = 100; d1 = 1;            # initial guess for a1 and b1
#regression fit regrFunc(x) "gnuplot.values" using 1:2 via k1, d1

set style line 1 pt 1 lt 1 lw 1 lc 0
set style line 2 pt 2 lt 2 lw 1 lc 0 
set style line 3 pt 8 lt 3 lw 1 lc 0
set style line 4 pt 4 lt 6 lw 1 lc 0 
set style line 5 pt 5 lt 1 lw 1 lc 0
set style line 6 pt 6 lt 4 lw 1 lc 0
set style line 7 pt 7 lt 7 lw 0 lc 0

set xlabel '<xlabel>'
set ylabel '<ylabel>'

set rmarg 2
set size 1,0.8
set key left top

#<extra>
#<extra1>

set term post eps enhanced solid "Helvetica" 22
set out "graph.eps"


plot	#<do1>#<plot1>"gnuplot.values" using 1:2 title '<title1>' with linespoints ls 2 \
		#<do2>#<plot2>, "gnuplot.values" using 1:3 title '<title2>' with linespoints ls 6 \
		#<do3>#<plot3>, "gnuplot.values" using 1:4 title '<title3>' with linespoints ls 4 \
		#<do4>#<plot4>, "gnuplot.values" using 1:5 title '<title4>' with linespoints ls 5 \
		#<do5>, "gnuplot.values" using 1:6 title '<title5>' with linespoints ls 1 \
		#<do6>, "gnuplot.values" using 1:7 title '<title6>' with linespoints ls 6 \
		#<do7>, "gnuplot.values" using 1:8 title '<title6>' with linespoints ls 4 \
		#<do8>, "gnuplot.values" using 1:9 title '<title6>' with linespoints ls 5 \
		#<do9>, "gnuplot.values" using 1:10 title '<title6>' with linespoints ls 5 \
		#k1 * x + d1 title 'Linear Regression' with lines lt 7