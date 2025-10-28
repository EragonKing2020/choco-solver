/*
 * This file is part of choco-solver, http://choco-solver.org/
 *
 * Copyright (c) 2025, IMT Atlantique. All rights reserved.
 *
 * Licensed under the BSD 4-clause license.
 *
 * See LICENSE file in the project root for full license information.
 */

package org.chocosolver.solver.search.loop.lns.neighbors;

import org.chocosolver.solver.Solution;
import org.chocosolver.solver.exception.ContradictionException;
import org.chocosolver.solver.variables.IntVar;

import java.util.*;
import java.util.stream.IntStream;


/**
 * A LNS that choose randomly some values and fix variables instantiated to one of these values.
 *
 * @author Nathan Rabier
 * @since 20/10/25
 */
public class RandomGroupedByValueNeighborhood extends IntNeighbor{

    /**
     * Number of variables to consider in this neighbor.
     */
    protected final int nbVars;
    /**
     * Global min value possible for the variables.
     */
    protected final int minVal;
    /**
     * Global max value possible for the variables.
     */
    protected final int maxVal;
    /**
     * Proportion of values to be fixed.
     */
    protected final float propFixValues;
    /**
     * Number of values used in the last solution.
     */
    protected int nbUsedValues;
    /**
     * For randomness.
     */
    private final Random rd;
    /**
     * Size of the fragment.
     */
    private double nbFixedValues = 0d;
    /**
     * Number of times this neighbor is called.
     */
    private int nbCall;
    /**
     * Next time the level should be increased.
     */
    protected int limit;
    /**
     * Relaxing factor.
     */
    protected final int level;
    /**
     * Indicate the index (in {@link #variables}) of the variables instantiated to the same value.
     * Ordered from {@link #minVal} to {@link #maxVal}.
     */
    private ArrayList<ArrayList<Integer>> valueToVariables;
    /**
     * Indicate which values are selected to be part of the fragment.
     */
    protected BitSet fragment;

    /**
     * Create a neighbor for LNS which randomly selects values of variables, and choose all the variables instantiated to one of these values to be part of a fragment.
     * @param vars variables to consider in this
     * @param propFixValues proportion of values to fix (between 0 and 1)
     * @param level relaxing factor
     * @param seed for randomness
     */
    public RandomGroupedByValueNeighborhood(IntVar[] vars, float propFixValues, int level, long seed){
        super(vars);
        this.nbVars = vars.length;
        this.level = level;
        this.rd = new Random(seed);
        this.propFixValues = propFixValues;
        this.minVal = Arrays.stream(vars).mapToInt(IntVar::getLB).min().getAsInt();
        this.maxVal = Arrays.stream(vars).mapToInt(IntVar::getUB).max().getAsInt();
        this.fragment = new BitSet(this.maxVal - this.minVal + 1);
        this.valueToVariables = new ArrayList<ArrayList<Integer>>(this.maxVal - this.minVal + 1);
        for (int j = 0; j < this.maxVal - this.minVal + 1; j++) this.valueToVariables.add(new ArrayList<Integer>());
    }

    @Override
    public void recordSolution() {
        super.recordSolution();
        nbCall = 0;
        limit = 0;
        updateValuesToVariables();
        nbFixedValues = propFixValues * nbUsedValues + 1;
    }

    @Override
    public void loadFromSolution(Solution solution) {
        super.loadFromSolution(solution);
        nbCall = 0;
        limit = 0;
        updateValuesToVariables();
        nbFixedValues = propFixValues * nbUsedValues + 1;
    }

    /**
     * Update the values of {@link #nbUsedValues} and {@link #valueToVariables}.
     */
    protected void updateValuesToVariables(){
        nbUsedValues = 0;
        for (ArrayList<Integer> v : valueToVariables) v.clear();
        for (int i = 0; i < nbVars; i++) {
            if (valueToVariables.get(values[i] - minVal).isEmpty()) nbUsedValues++;
            valueToVariables.get(values[i] - minVal).add(i);
        }
    }

    @Override
    public void fixSomeVariables() throws ContradictionException {
        nbCall ++;
        restrictLess();
        fragment.set(0, this.maxVal - this.minVal + 1);
        for (int j = 0; j < this.maxVal - this.minVal + 1; j ++) if (valueToVariables.get(j).isEmpty()) fragment.clear(j);
        for (int i = 0; i < nbFixedValues; i++) {
            int idValue = selectValue();
            for (int idVariable : valueToVariables.get(idValue)) {
                if (variables[idVariable].contains(values[idVariable])) {  // to deal with objective variable and related
                    freeze(idVariable);
                }
            }
            fragment.clear(idValue);
        }
    }

    /**
     * Select the next value to consider to create the fragment.
     * @return the id of the chosen value.
     */
    protected int selectValue() {
        int id = 0;
        int cc = rd.nextInt(fragment.cardinality());
        for (id = fragment.nextSetBit(0); id >= 0 && cc > 0; id = fragment.nextSetBit(id + 1)) {
            cc--;
        }
        return id;
    }


    @Override
    public void restrictLess() {
        if (nbCall > limit) {
            limit = nbCall + level;
            nbFixedValues = rd.nextDouble() * nbUsedValues;
        }
    }
}
