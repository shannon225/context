package org.searlelab.context.percolator;

import java.util.LinkedHashSet;
import java.util.List;

import edu.washington.gs.maccoss.encyclopedia.utils.Logger;

public class TrainingSeeds {
	
	public static final int DEFAULT_SEED = 1;
	
	private TrainingSeeds() {} 
	
	// Seeds are accepted as training runs (runs the model training multiple times, this does not match folds across engines)
	public static int requireValid(int seed) {
		
		if (seed < 1) throw new IllegalArgumentException("Training seeds must be positive integers from 1 to 2147483647.");
		return seed;
		
	}
	
	public static List<Integer> parse(String value) {
		
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("--seeds should be formatted as a comma-separated list (for ex. 1,2,3 for multiple seeds to 1 for 1 seed).");
		}
		
		LinkedHashSet<Integer> seeds = new LinkedHashSet<>();
		
		for (String token : value.split(",", -1)) {
			final int seed; 
			try {
				seed = requireValid(Integer.parseInt(token.trim()));
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid training seed [" + token + "]; expected an integer betweeen 1 and 214783647.", e);
			}
			
			if (!seeds.add(seed)) throw new IllegalArgumentException("Duplicate training seeds: " + seed);
		}
		
		return List.copyOf(seeds);
	}
	
	public static List<Integer> validate(List<Integer> seeds) {
		if (seeds == null || seeds.isEmpty()) throw new IllegalArgumentException("At least one seed is required.");
		
		LinkedHashSet<Integer> distinct = new LinkedHashSet<>(); 
		
		for (Integer seed : seeds) {
			if (seed == null) throw new IllegalArgumentException("Training seeds must not be null.");			
				requireValid(seed);
			if(!distinct.add(seed)) throw new IllegalArgumentException("Duplicate training seed: " + seed);
		}
		return List.copyOf(distinct);
	}
}
