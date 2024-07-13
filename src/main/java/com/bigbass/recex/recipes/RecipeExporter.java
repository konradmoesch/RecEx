package com.bigbass.recex.recipes;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Collection;

import com.bigbass.recex.RecipeExporterMod;
import com.bigbass.recex.recipes.gregtech.GregtechMachine;
import com.bigbass.recex.recipes.gregtech.GregtechRecipe;
import com.bigbass.recex.recipes.gregtech.RecipeUtil;
import com.bigbass.recex.recipes.ingredients.Fluid;
import com.bigbass.recex.recipes.ingredients.Item;
import com.bigbass.recex.recipes.ingredients.ItemOreDict;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import gregtech.api.util.GT_LanguageManager;
import gregtech.api.util.GT_Recipe;
import gregtech.api.recipe.RecipeMap;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;

public class RecipeExporter {
	
	private static RecipeExporter instance;
	
	private RecipeExporter(){}
	
	public static RecipeExporter getInst(){
		if(instance == null){
			instance = new RecipeExporter();
		}
		
		return instance;
	}
	
	/**
	 * <p>Collects recipes into a master Hashtable (represents a JSON Object),
	 * then serializes it and saves it to a datetime-stamped file.</p>
	 * 
	 * <p>Recipes are stored in collections, often either List's or Hashtable's.
	 * The Gson library will serialize objects based on their public fields.
	 * The field name becomes the key, and the value is also serialized the same way.
	 * Lists are serialized as JSON arrays.</p>
	 * 
	 * <p>Schema for existing recipe sources should not be radically changed unless
	 * truly necessary. Adding additional data is acceptable however.</p>
	 */
	public void run(){
		Hashtable<String, Object> root = new Hashtable<String, Object>();
		
		List<Object> sources = new ArrayList<Object>();
		sources.add(getGregtechRecipes());
		sources.add(getShapedRecipes());
		sources.add(getShapelessRecipes());
		sources.add(getOreDictShapedRecipes());
		//TODO Support OreDictShapelessRecipes
		
		root.put("sources", sources);
		
		Gson gson = (new GsonBuilder()).serializeNulls().create();
		try {
			saveData(gson.toJson(root));
		} catch(Exception e){
			e.printStackTrace();
			RecipeExporterMod.log.error("Recipes failed to export!");
		}
	}
	
	/**
	 * <p>Unlike vanilla recipes, the current schema here groups recipes from each machine together.
	 * This is a minor file size improvement. Rather than specifying the machine's name in every recipe,
	 * the machine name is only listed once for the entire file.</p>
	 * 
	 * <p>This format does not impede the process of loading the recipes into NEP.</p>
	 */
	private Object getGregtechRecipes(){
		Hashtable<String, Object> data = new Hashtable<String, Object>();
		
		data.put("type", "gregtech");
		
		List<GregtechMachine> machines = new ArrayList<GregtechMachine>();
		for(RecipeMap map : RecipeMap.ALL_RECIPE_MAPS.values()){
			GregtechMachine mach = new GregtechMachine();
			
			// machine name retrieval
			mach.n = GT_LanguageManager.getTranslation(map.unlocalizedName);
			if(mach.n == null || mach.n.isEmpty()){
				mach.n = map.unlocalizedName;
			}
			
			Collection<GT_Recipe> coll = map.getAllRecipes();
			//for(GT_Recipe rec : map.getAllRecipes()){
			for(GT_Recipe rec : coll){
				GregtechRecipe gtr = new GregtechRecipe();
				gtr.en = rec.mEnabled;
				gtr.dur = rec.mDuration;
				gtr.eut = rec.mEUt;
				
				// item inputs
				for(ItemStack stack : rec.mInputs){
					Item item = RecipeUtil.formatGregtechItemStack(stack);
					
					if(item == null){
						continue;
					}
					
					gtr.iI.add(item);
				}
				
				// item outputs
				for(ItemStack stack : rec.mOutputs){
					Item item = RecipeUtil.formatGregtechItemStack(stack);
					
					if(item == null){
						continue;
					}
					
					gtr.iO.add(item);
				}
				
				// fluid inputs
				for(FluidStack stack : rec.mFluidInputs){
					Fluid fluid = RecipeUtil.formatGregtechFluidStack(stack);
					
					if(fluid == null){
						continue;
					}
					
					gtr.fI.add(fluid);
				}
				
				// fluid outputs
				for(FluidStack stack : rec.mFluidOutputs){
					Fluid fluid = RecipeUtil.formatGregtechFluidStack(stack);
					
					if(fluid == null){
						continue;
					}
					
					gtr.fO.add(fluid);
				}
				
				mach.recs.add(gtr);
			}
			machines.add(mach);
		}
		
		data.put("machines", machines);
		
		return data;
	}
	
	private Object getShapedRecipes(){
		Hashtable<String, Object> data = new Hashtable<String, Object>();
		
		data.put("type", "shaped");
		
		List<ShapedRecipe> retRecipes = new ArrayList<ShapedRecipe>();
		List<?> recipes = CraftingManager.getInstance().getRecipeList();
		for(Object obj : recipes){
			if(obj instanceof ShapedRecipes){
				ShapedRecipes original = (ShapedRecipes) obj;
				ShapedRecipe rec = new ShapedRecipe();
				
				for(ItemStack stack : original.recipeItems){
					Item item = RecipeUtil.formatRegularItemStack(stack);
					rec.iI.add(item);
				}
				
				rec.o = RecipeUtil.formatRegularItemStack(original.getRecipeOutput());
				
				retRecipes.add(rec);
			}
		}
		data.put("recipes", retRecipes);
		
		return data;
	}

	private Object getShapelessRecipes(){
		Hashtable<String, Object> data = new Hashtable<String, Object>();
		
		data.put("type", "shapeless");
		
		List<ShapelessRecipe> retRecipes = new ArrayList<ShapelessRecipe>();
		List<?> recipes = CraftingManager.getInstance().getRecipeList();
		for(Object obj : recipes){
			if(obj instanceof ShapelessRecipes){
				ShapelessRecipes original = (ShapelessRecipes) obj;
				ShapelessRecipe rec = new ShapelessRecipe();
				
				for(Object stack : original.recipeItems){
					if(stack instanceof ItemStack){
						rec.iI.add(RecipeUtil.formatRegularItemStack((ItemStack) stack));
					} else if(stack instanceof net.minecraft.item.Item){
						rec.iI.add(RecipeUtil.formatRegularItemStack(new ItemStack((net.minecraft.item.Item) stack)));
					}
				}
				
				rec.o = RecipeUtil.formatRegularItemStack(original.getRecipeOutput());
				
				retRecipes.add(rec);
			}
		}
		data.put("recipes", retRecipes);
		
		return data;
	}
	
	private Object getOreDictShapedRecipes(){
		Hashtable<String, Object> data = new Hashtable<String, Object>();

		data.put("type", "shapedOreDict");

		List<OreDictShapedRecipe> retRecipes = new ArrayList<OreDictShapedRecipe>();
		List<?> recipes = CraftingManager.getInstance().getRecipeList();
		for(Object obj : recipes){
			if(obj instanceof ShapedOreRecipe){
				ShapedOreRecipe original = (ShapedOreRecipe) obj;
				OreDictShapedRecipe rec = new OreDictShapedRecipe();
				
				for(Object input : original.getInput()){
					if(input instanceof ItemStack) {
						rec.iI.add(RecipeUtil.formatRegularItemStack((ItemStack) input));
					} else if (input instanceof String){
						ItemOreDict item = RecipeUtil.parseOreDictionary((String) input);
						if(item != null){
							rec.iI.add(item);
							RecipeExporterMod.log.info("input instanceof String : " + item.dns + ", " + item.ims);
						}
					} else if (input instanceof String[]){
						ItemOreDict item = RecipeUtil.parseOreDictionary((String[]) input);
						if(item != null){
							rec.iI.add(item);
							RecipeExporterMod.log.info("input instanceof String[] : " + item.dns + ", " + item.ims);
						}
					} else if (input instanceof net.minecraft.item.Item){
						rec.iI.add(RecipeUtil.formatRegularItemStack(new ItemStack((net.minecraft.item.Item)input)));
					} else if (input instanceof Block){
						rec.iI.add(RecipeUtil.formatRegularItemStack(new ItemStack((Block)input,1,Short.MAX_VALUE)));
					} else if (input instanceof ArrayList<?>) {
						ArrayList<?> list = (ArrayList<?>) input;
						if(list != null && list.size() > 0){
							ItemOreDict item = new ItemOreDict();
							for(Object listObj : list){
								if(listObj instanceof ItemStack){
									ItemStack stack = (ItemStack) listObj;
									item.ims.add(RecipeUtil.formatRegularItemStack(stack));
									
									int[] ids = OreDictionary.getOreIDs(stack);
									for(int id : ids){
										String name = OreDictionary.getOreName(id);
										if(name != null && !name.isEmpty() && !name.equalsIgnoreCase("Unknown")){
											boolean isDuplicate = false;
											for(String existing : item.dns){
												if(existing.equalsIgnoreCase(name)){
													isDuplicate = true;
													break;
												}
											}
											if(!isDuplicate){
												item.dns.add(name);
											}
										}
									}
								}
							}
							
							if(!item.ims.isEmpty()){
								rec.iI.add(item);
							}
						}
					} else if (input != null){
						try {
							RecipeExporterMod.log.warn("OreDict Input Type not parsed! " + input.getClass().getTypeName() + " | " + input.getClass().getName());
						} catch(NullPointerException e){}
					}
				}
				
				rec.o = RecipeUtil.formatRegularItemStack(original.getRecipeOutput());
				
				retRecipes.add(rec);
			}
		}
		data.put("recipes", retRecipes);

		return data;
	}
	
	private void saveData(String json){
		final File saveFile = getSaveFile();
		
		try {
			FileWriter writer = new FileWriter(saveFile);
			writer.write(json);
			writer.close();
			
			RecipeExporterMod.log.info("Recipes have been exported.");
		} catch (IOException | NullPointerException e) {
			e.printStackTrace();
			RecipeExporterMod.log.error("Recipes failed to save!");
			return;
		}
		
		final String zipPath = saveFile.getPath().replace(".json", ".zip");
		final ZipFile zipFile = new ZipFile(new File(zipPath));
		final ZipParameters zipParameters = new ZipParameters();
		zipParameters.setCompressionMethod(CompressionMethod.DEFLATE);
		zipParameters.setCompressionLevel(CompressionLevel.FASTEST);
		
		try {
			zipFile.addFile(saveFile, zipParameters);
			RecipeExporterMod.log.info("Recipes have been compressed.");
		} catch(Exception e) {
			e.printStackTrace();
			RecipeExporterMod.log.warn("Recipe compression may have failed!");
		}
	}
	
	private File getSaveFile(){
		String dateTime = ZonedDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm-ss"));
		File file = new File(RecipeExporterMod.clientConfigDir.getParent() + "/RecEx-Records/" + dateTime + ".json");
		if(!file.exists()){
			file.getParentFile().mkdirs();
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return file;
	}
}
