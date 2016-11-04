/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.java19modules;

import com.intellij.codeInsight.daemon.impl.analysis.JavaModuleGraphUtil;
import com.intellij.codeInspection.BaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Pavel.Dolgov
 */
public class Java9NonAccessibleTypeExposedInspection extends BaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    PsiFile file = holder.getFile();
    if (file instanceof PsiJavaFile) {
      PsiJavaFile javaFile = (PsiJavaFile)file;
      if (javaFile.getLanguageLevel().isAtLeast(LanguageLevel.JDK_1_9)) {
        PsiJavaModule psiModule = JavaModuleGraphUtil.findDescriptorByElement(file);
        if (psiModule != null) {
          VirtualFile vFile = file.getVirtualFile();
          if (vFile != null) {
            Module module = ProjectFileIndex.SERVICE.getInstance(holder.getProject()).getModuleForFile(vFile);
            if (module != null) {
              Set<String> exportedPackageNames = new THashSet<>(ContainerUtil.mapNotNull(psiModule.getExports(), p -> p.getPackageName()));
              if (exportedPackageNames.contains(javaFile.getPackageName())) {
                return new NonAccessibleTypeExposedVisitor(holder, module, exportedPackageNames);
              }
            }
          }
        }
      }
    }
    return PsiElementVisitor.EMPTY_VISITOR;
  }

  private static class NonAccessibleTypeExposedVisitor extends JavaElementVisitor {
    private final ProblemsHolder myHolder;
    private final ModuleFileIndex myModuleFileIndex;
    private final Set<String> myExportedPackageNames;

    public NonAccessibleTypeExposedVisitor(ProblemsHolder holder, Module module, Set<String> exportedPackageNames) {
      myHolder = holder;
      myModuleFileIndex = ModuleRootManager.getInstance(module).getFileIndex();
      myExportedPackageNames = exportedPackageNames;
    }

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (isModulePublicApi(method)) {
        if (!method.isConstructor()) {
          checkType(method.getReturnType(), method.getReturnTypeElement());
        }
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
          checkType(parameter.getType(), parameter.getTypeElement());
        }
      }
    }

    @Override
    public void visitField(PsiField field) {
      super.visitField(field);
      if (isModulePublicApi(field)) {
        checkType(field.getType(), field.getTypeElement());
      }
    }

    private void checkType(@Nullable PsiType type, @Nullable PsiTypeElement typeElement) {
      if (typeElement != null) {
        PsiClass psiClass = PsiUtil.resolveClassInType(type);
        if (psiClass != null && isInModuleSource(psiClass) && !isModulePublicApi(psiClass)) {
          myHolder.registerProblem(typeElement, "The class is not exported from the module");
        }
      }
    }

    @Contract("null -> false")
    private boolean isModulePublicApi(@Nullable PsiMember member) {
      if (member != null && isModulePublicApiMember(member)) {
        PsiElement parent = member.getParent();
        if (parent instanceof PsiClass) {
          return isModulePublicApi((PsiClass)parent);
        }
        if (parent instanceof PsiJavaFile) {
          String packageName = ((PsiJavaFile)parent).getPackageName();
          return myExportedPackageNames.contains(packageName);
        }
      }
      return false;
    }

    private static boolean isModulePublicApiMember(@NotNull PsiMember member) {
      if (member.hasModifierProperty(PsiModifier.PUBLIC) || member.hasModifierProperty(PsiModifier.PROTECTED)) {
        return true;
      }
      PsiClass containingClass = member.getContainingClass();
      return containingClass != null && containingClass.isInterface();
    }

    private boolean isInModuleSource(@NotNull PsiClass psiClass) {
      PsiFile psiFile = psiClass.getContainingFile();
      if (psiFile != null) {
        VirtualFile vFile = psiFile.getVirtualFile();
        if (vFile != null) {
          return myModuleFileIndex.isInSourceContent(vFile);
        }
      }
      return false;
    }
  }
}
