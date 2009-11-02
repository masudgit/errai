package org.jboss.errai.widgets.rebind;

import com.google.gwt.core.ext.Generator;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;
import com.google.gwt.core.ext.typeinfo.JField;
import com.google.gwt.core.ext.typeinfo.JParameterizedType;
import com.google.gwt.core.ext.typeinfo.TypeOracle;
import com.google.gwt.user.rebind.ClassSourceFileComposerFactory;
import com.google.gwt.user.rebind.SourceWriter;
import org.jboss.errai.widgets.client.mapping.ErraiWidgetBinding;
import org.jboss.errai.widgets.client.mapping.WidgetMapper;
import org.mvel2.templates.CompiledTemplate;
import org.mvel2.templates.TemplateCompiler;
import org.mvel2.templates.TemplateRuntime;

import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class WidgetMappingsGenerator extends Generator {


    /**
     * Simple name of class to be generated
     */
    private String className = null;

    private JClassType targetClass = null;

    /**
     * Package name of class to be generated
     */
    private String packageName = null;
    private String strTargetType;
    private String typeName;

    private TypeOracle typeOracle;

    private CompiledTemplate mappingsGen;

    @Override
    public String generate(TreeLogger logger, GeneratorContext context, String typeName) throws UnableToCompleteException {
        typeOracle = context.getTypeOracle();
        this.typeName = typeName;

        InputStream istream = this.getClass().getResourceAsStream("EntityMappingGenerator.mv");
        mappingsGen = TemplateCompiler.compileTemplate(istream, null);

        try {
            // get classType and save instance variables

            JClassType classType = typeOracle.getType(typeName);
            JClassType widgetBindingIface = typeOracle.getType(ErraiWidgetBinding.class.getName());

            for (JClassType t : classType.getImplementedInterfaces()) {
                if (widgetBindingIface.isAssignableFrom(t)) {
                    JParameterizedType pt = t.isParameterized();

                    if (pt != null) {
                        if (pt.getTypeArgs().length == 1) {
                            JClassType p = pt.getTypeArgs()[0];

                            String name = p.getName();
                            String pkg = p.getPackage().getName();
                            targetClass = typeOracle.getType(pkg + "." + name);
                        }
                    }
                }
            }

            if (targetClass == null) {
                throw new RuntimeException("Type " + ErraiWidgetBinding.class.getSimpleName()
                        + " must be bound via an extended interface with a type parameter " +
                        "(eg. interface WidgetBinding extends " + ErraiWidgetBinding.class.getSimpleName() + "<MyWidget> {} )");
            }

            packageName = classType.getPackage().getName();
            className = targetClass.getName().replaceAll("\\.", "_") + classType.getSimpleSourceName();
            strTargetType = targetClass.getPackage().getName() + "." + targetClass.getName();

            logger.log(TreeLogger.INFO, "Generating Extensions Bootstrapper...");

            // Generate class source code
            generateClass(logger, context);

        }
        catch (Exception e) {

            // record sendNowWith logger that Map generation threw an exception
            logger.log(TreeLogger.ERROR, "Error generating extensions", e);
            e.printStackTrace();
        }

        // return the fully qualifed name of the class generated
        return packageName + "." + className;
    }

    /**
     * Generate source code for new class. Class extends
     * <code>HashMap</code>.
     *
     * @param logger  Logger object
     * @param context Generator context
     */
    private void generateClass(TreeLogger logger, GeneratorContext context) {
        // get print writer that receives the source code
        PrintWriter printWriter = context.tryCreate(logger, packageName, className);
        // print writer if null, source code has ALREADY been generated,

        if (printWriter == null) return;

        // init composer, set class properties, create source writer
        ClassSourceFileComposerFactory composer = new ClassSourceFileComposerFactory(packageName,
                className);
        composer.addImplementedInterface(typeName);

        SourceWriter sourceWriter = composer.createSourceWriter(context, printWriter);

        // generator constructor source code
        generateExtensions(context, logger, sourceWriter);
        // close generated class
        sourceWriter.outdent();
        sourceWriter.println("}");

        // commit generated class
        context.commit(logger, printWriter);

    }

    private void generateExtensions(GeneratorContext context, TreeLogger logger, SourceWriter sourceWriter) {
        // start constructor source generation
        sourceWriter.println("public " + className + "() { ");
        sourceWriter.println("}");

        sourceWriter.println("public void mapAll(" + strTargetType + " widget) { ");
        sourceWriter.outdent();

        try {
            Map<String, String> mapperFields = new HashMap<String, String>();

            int i = 1;
            for (JField f : targetClass.getFields()) {
                if (f.isAnnotationPresent(EntityMapped.class)) {
                    EntityMapped m = f.getAnnotation(EntityMapped.class);

                    Class entityTarget = m.value();
                    JClassType jEntityTarget = typeOracle.getType(entityTarget.getName());

                    String varName = f.getName() + "Mapper";
                    mapperFields.put(f.getName(), varName);

                    List<JField> fieldsToMap = new LinkedList<JField>();

                    if (f.isAnnotationPresent(EntityFields.class)) {
                        EntityFields ef = f.getAnnotation(EntityFields.class);

                        for (String fieldName : ef.values()) {
                            fieldsToMap.add(targetClass.getField(fieldName));
                        }
                    } else {
                        for (JField fld : jEntityTarget.getFields()) {
                            if (fld.getEnclosingType().equals(jEntityTarget)) {
                                fieldsToMap.add(fld);
                            }
                        }
                    }

                    Map<String, Object> vars = new HashMap<String, Object>();
                    vars.put("typeOracle", typeOracle);
                    vars.put("variableName", varName);
                    vars.put("targetWidget", f.getType().isClassOrInterface().getPackage().getName()
                            + "." + f.getType().isClassOrInterface().getName());
                    vars.put("targetType", entityTarget.getName());
                    vars.put("targetFieldName", f.getName());
                    vars.put("fieldsToMap", fieldsToMap);

                    String s = (String) TemplateRuntime.execute(mappingsGen, vars);

                    System.out.println(s);

                    sourceWriter.print(s);
                }
            }

            JClassType widgetMapper = typeOracle.getType(WidgetMapper.class.getName());

            for (JField f : targetClass.getFields()) {
                if (f.isAnnotationPresent(MapperField.class) && widgetMapper.isAssignableFrom(f.getType().isClassOrInterface())) {
                    MapperField mf = f.getAnnotation(MapperField.class);

                    String varName = mapperFields.get(mf.value());

                    if (varName == null) {
                        logger.log(TreeLogger.Type.ERROR, "mapper field '" + mf.value() + "' does not exist");
                        throw new RuntimeException("mapper field '" + mf.value() + "' does not exist");
                    }

                    System.out.println();

                    String s = "widget." + f.getName() + " = " + varName + ";";

                    System.out.println(s);

                    sourceWriter.println(s);
                }
            }

        }
        catch (Exception e) {
            logger.log(TreeLogger.Type.ERROR, "failed to map field (does not exist)", e);
            e.printStackTrace();
        }

        // end constructor source generation
        sourceWriter.outdent();
        sourceWriter.println("}");
    }

}
