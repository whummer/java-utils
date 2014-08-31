#f1(x) = k1 * x + d1              # define the function to be fit
#k1 = 100; d1 = 1;            # initial guess for a1 and b1
#fit f1(x) "gnuplot.values" using 1:2 via k1, d1

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
set key left top

##Defining variables
numPlots = 6
SX = 2;
OX = 0.0; OY = 0.0
NX = 1; NY = 4
PY = 0.35
#<do4>PY = 0.25
#<do5>PY = 0.2
#<do6>PY = 0.1667
SY=PY-0.03

#set size SX*NX+OX*1.2, SY*NY+OY*1.8 

#<extra>

set lmargin 10 
set bmargin 1
set term post eps enhanced solid "Helvetica" 22
set out "graph.eps"

set multiplot
set size SX,SY 
set origin OX,OY
set tmargin 0
set bmargin 0
set grid
set ytics autofreq
set ytics font "Arial,15"
set offset 0.2, 0.2, 0.1, 0.1 
set autoscale y
#<extra1>

#<plot1>plot	"gnuplot.values" using 1:2 title '<title1>' with linespoints ls 1
set xlabel ''
set format x ''
set origin OX,OY + PY
#<extra2>
#<plot2>plot 	"gnuplot.values" using 1:3 title '<title2>' with linespoints ls 2
set origin OX,OY + 2*PY
#<extra3>
#<plot3>plot	"gnuplot.values" using 1:4 title '<title3>' with linespoints ls 3
#<do4>set origin OX,OY + 3*PY
#<do4>#<extra4>
#<do4>#<plot4>plot "gnuplot.values" using 1:5 title '<title4>' with linespoints ls 4
#<do5>set origin OX,OY + 4*PY
#<do5>#<extra5>
#<do5>#<plot5>plot "gnuplot.values" using 1:6 title '<title5>' with linespoints ls 5
#<do6>set origin OX,OY + 5*PY
#<do6>#<extra6>
#<do6>#<plot6>plot "gnuplot.values" using 1:7 title '<title6>' with linespoints ls 6
#<do7>set origin OX,OY + 6*PY
#<do7>#<extra7>
#<do7>#<plot7>plot "gnuplot.values" using 1:8 title '<title7>' with linespoints ls 7
#<do8>set origin OX,OY + 7*PY
#<do8>#<extra8>
#<do8>#<plot8>plot "gnuplot.values" using 1:9 title '<title8>' with linespoints ls 8
unset multiplot 
