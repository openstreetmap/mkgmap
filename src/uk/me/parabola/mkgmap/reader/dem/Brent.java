/*
 * Copyright (C) 2009 Christian Gawron
 * 
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2 as
 *  published by the Free Software Foundation.
 * 
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 * 
 * 
 * Author: Christian Gawron
 * Create date: 03-Jul-2009
 */
package uk.me.parabola.mkgmap.reader.dem;

/**
 * Find zero of a function using Brent's method.
 */
public class Brent
{
    
    public interface Function
    {
	public double eval(double x);
    }


    static double epsilon = 3.0e-10;

    public static double zero(Function f, double x1, double x2) 
    {
	return zero(f, x1, x2, 1e-8, 100);
    }

    public static double zero(Function f, double x1, double x2, double tol, int maxit)
    {
	double a=x1, b=x2, c=x2, d=0, e=0, min1, min2;
	double fa=f.eval(a), fb=f.eval(b), fc;
	double p, q, r, s, tolerance, xm;

	if ((fa > 0.0 && fb > 0.0) || (fa < 0.0 && fb < 0.0))
	    throw new ArithmeticException("Root must be bracketed");

	fc=fb;
	for (int iterations=0; iterations < maxit; iterations++) 
	{
	    if ((fb > 0.0 && fc > 0.0) || (fb < 0.0 && fc < 0.0)) {
		c=a;
		fc=fa;
		e=d=b-a;
	    }
	    if (Math.abs(fc) < Math.abs(fb)) {
		a=b;
		b=c;
		c=a;
		fa=fb;
		fb=fc;
		fc=fa;
	    }
	    tolerance=2.0*epsilon*Math.abs(b)+0.5*tol;
	    xm=0.5*(c-b);
	    if (Math.abs(xm) <= tolerance || fb == 0.0) return b;
	    if (Math.abs(e) >= tolerance && Math.abs(fa) > Math.abs(fb)) {
		s=fb/fa;
		if (a == c) {
		    p=2.0*xm*s;
		    q=1.0-s;
		} else {
		    q=fa/fc;
		    r=fb/fc;
		    p=s*(2.0*xm*q*(q-r)-(b-a)*(r-1.0));
		    q=(q-1.0)*(r-1.0)*(s-1.0);
		}
		if (p > 0.0) q = -q;
		p=Math.abs(p);
		min1=3.0*xm*q-Math.abs(tolerance*q);
		min2=Math.abs(e*q);
		if (2.0*p < (min1 < min2 ? min1 : min2)) {
		    e=d;
		    d=p/q;
		} else {
		    d=xm;
		    e=d;
		}
	    } else {
		d=xm;
		e=d;
	    }
	    a=b;
	    fa=fb;
	    if (Math.abs(d) > tolerance)
		b += d;
	    else
		b += xm >= 0 ? tolerance : -tolerance;
	    fb=f.eval(b);
	}
	throw new ArithmeticException("Maximum number of iterations exceeded");
    }
}