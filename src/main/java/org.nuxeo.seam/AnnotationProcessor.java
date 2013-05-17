package org.nuxeo.seam;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.SourceVersion;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.util.Set;
import java.io.PrintWriter;
import java.io.IOException;

@SupportedAnnotationTypes({"org.jboss.seam.annotations.Name"})
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public class AnnotationProcessor extends AbstractProcessor {

    public static final String SEAM_BEANS = "seam.beans";

    Messager messager;
    PrintWriter out = null;

    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        messager = processingEnv.getMessager();

        try {
            FileObject file = processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, "", SEAM_BEANS);
            out = new PrintWriter(file.openWriter());
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, e.toString());
        }
    }

    public boolean process(Set<? extends TypeElement> annotations,
                           RoundEnvironment env) {

        for (TypeElement te : annotations) {
            for (Element e: env.getElementsAnnotatedWith(te) ) {
                messager.printMessage(Diagnostic.Kind.NOTE, "Found: " + e.toString());
                out.println(e.toString());
            }
        }

        out.flush();

        return true;
    }
}