/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.java.hints.jdk;

import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.DeconstructionPatternTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PatternTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.TreePath;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.TreePathScanner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.java.source.CompilationInfo;
import org.netbeans.api.java.source.CodeStyleUtils;
import org.netbeans.api.java.source.WorkingCopy;
import org.netbeans.api.java.queries.CompilerOptionsQuery;
import org.netbeans.modules.java.hints.errors.Utilities;
import org.netbeans.spi.editor.hints.ErrorDescription;
import org.netbeans.spi.editor.hints.Fix;
import org.netbeans.spi.java.hints.ErrorDescriptionFactory;
import org.netbeans.spi.java.hints.Hint;
import org.netbeans.spi.java.hints.HintContext;
import org.netbeans.spi.java.hints.JavaFix;
import org.netbeans.spi.java.hints.MatcherUtilities;
import org.netbeans.spi.java.hints.TriggerTreeKind;
import org.openide.util.NbBundle;

@NbBundle.Messages({
    "DN_ConvertToNestedRecordPattern=Convert to nested record pattern",
    "DESC_ConvertToNestedRecordPattern=Convert to nested record pattern",
    "ERR_ConvertToNestedRecordPattern=Convert to nested record pattern",
    "FIX_ConvertToNestedRecordPattern=Convert to nested record pattern"
})
@Hint(displayName = "#DN_ConvertToNestedRecordPattern", description = "#DESC_ConvertToNestedRecordPattern", category = "rules15",
        minSourceVersion = "19")
/**
 *
 * @author mjayan
 */
public class ConvertToNestedRecordPattern {

    private static final int RECORD_PATTERN_PREVIEW_JDK_VERSION = 19;

    @TriggerTreeKind(Tree.Kind.DECONSTRUCTION_PATTERN)
    public static ErrorDescription convertToNestedRecordPattern(HintContext ctx) {
        if (Utilities.isJDKVersionLower(RECORD_PATTERN_PREVIEW_JDK_VERSION) && !CompilerOptionsQuery.getOptions(ctx.getInfo().getFileObject()).getArguments().contains("--enable-preview")) {
            return null;
        }
        TreePath t = ctx.getPath();  // add the nested patterns to a list 

        if (!t.getParentPath().getLeaf().getKind().equals(Tree.Kind.INSTANCE_OF)) {
            return null;
        }
        List<String> treeList = new ArrayList<>();
        Map<PatternTree, List<PatternTree>> recordMap = new LinkedHashMap<>();
        DeconstructionPatternTree recordPattern = (DeconstructionPatternTree) t.getLeaf();
        recordMap = findNested(recordPattern, recordMap);  // contains POint p, COlor c, ColoredPoint lr binding pattern list

        for (PatternTree p : recordMap.keySet()) {
            BindingPatternTree bTree = (BindingPatternTree) p;
            treeList.add(bTree.getVariable().getName().toString());
        }

        while (t != null && t.getLeaf().getKind() != Tree.Kind.IF) {  //TO BE MODIFIED
            t = t.getParentPath();
        }
        Set<Tree> convertPath = new HashSet<>();
        List<String> localVarList = new ArrayList<>();
        Map<String, List<UserVariables>> userVars = new HashMap<>();

        new TreePathScanner<Void, Void>() {

            @Override
            public Void visitVariable(VariableTree node, Void p) {
                localVarList.add(node.getName().toString());
                Map<String, TreePath> outerVariables = new HashMap<String, TreePath>();
                Map<String, String> innerVariables = new HashMap<>();
                List<UserVariables> nList = new ArrayList<>();
                boolean match = MatcherUtilities.matches(ctx, getCurrentPath(), "$type $var1 = $expr3.$meth1()", outerVariables, new HashMap<String, Collection<? extends TreePath>>(), innerVariables);

                if (match && treeList.contains(outerVariables.get("$expr3").getLeaf().toString())) {
                    String expr3 = outerVariables.get("$expr3").getLeaf().toString();
                    if (!innerVariables.get("$var1").equals(innerVariables.get("$meth1"))) { //what of this is equal
                        nList.clear();
                        if (userVars.get(expr3) != null) {
                            nList = userVars.get(expr3);
                        }
                        nList.add(new UserVariables(innerVariables.get("$var1"), innerVariables.get("$meth1")));
                        userVars.put(expr3, nList);
                    }
                    convertPath.add(getCurrentPath().getLeaf());
                }
                return super.visitVariable(node, p);
            }
        }.scan(t, null);

        if (!convertPath.isEmpty()) {
            Fix fix = new FixImpl(ctx.getInfo(), ctx.getPath(), convertPath, recordMap, localVarList, userVars).toEditorFix();
            return ErrorDescriptionFactory.forName(ctx, ctx.getPath(), Bundle.ERR_ConvertToNestedRecordPattern(), fix);
        }
        return null;
    }

    private static Map<PatternTree, List<PatternTree>> findNested(PatternTree pTree, Map<PatternTree, List<PatternTree>> recordMap) {
        if (pTree instanceof BindingPatternTree) {
            recordMap.put(pTree, null);
            return recordMap;
        } else {
            DeconstructionPatternTree bTree = (DeconstructionPatternTree) pTree;
            for (PatternTree p : bTree.getNestedPatterns()) {
                findNested(p, recordMap);
            }
        }
        return recordMap;
    }

    private static class UserVariables {

        String methodName;
        String variable;

        UserVariables(String variable, String methodName) {
            this.variable = variable;
            this.methodName = methodName;
        }

        public String getMethodName() {
            return methodName;
        }

        public String getVariable() {
            return variable;
        }
    }

    private static final class FixImpl extends JavaFix {

        private final Map<PatternTree, List<PatternTree>> recordMap;
        private final Map<String, List<UserVariables>> userVars;
        private final Set<Tree> replaceOccurrences;
        List<String> localVarList;
        CompilationInfo info;

        public FixImpl(CompilationInfo info, TreePath main, Set<Tree> replaceOccurrences, Map<PatternTree, List<PatternTree>> recordMap, List<String> localVarList, Map<String, List<UserVariables>> userVars) {
            super(info, main);
            this.info = info;
            this.recordMap = recordMap;
            this.replaceOccurrences = replaceOccurrences;
            this.userVars = userVars;
            this.localVarList = localVarList;
        }

        @Override
        protected String getText() {
            return Bundle.ERR_ConvertToNestedRecordPattern();
        }

        @Override
        protected void performRewrite(JavaFix.TransformationContext ctx) {
            WorkingCopy wc = ctx.getWorkingCopy();
            TreePath t = ctx.getPath();
            TypeElement type = null;

            for (PatternTree p : recordMap.keySet()) {
                List<PatternTree> bindTree = new ArrayList<>();
                BindingPatternTree bTree = (BindingPatternTree) p;
                VariableTree v = (VariableTree) bTree.getVariable();
                type = (TypeElement) info.getTrees().getElement(TreePath.getPath(t, v.getType()));
                if (type == null || type.getRecordComponents().size() == 0) {
                    continue;
                }
                outer:
                for (RecordComponentElement recordComponent : type.getRecordComponents()) {
                    String name = recordComponent.getSimpleName().toString();
                    String returnType = recordComponent.getAccessor().getReturnType().toString();
                    returnType = returnType.substring(returnType.lastIndexOf(".") + 1);
                    if (userVars.get(v.getName().toString()) != null) {
                        for (UserVariables var : userVars.get(v.getName().toString())) {
                            if (var.getMethodName().equals(name)) {
                                bindTree.add((BindingPatternTree) wc.getTreeMaker().BindingPattern(wc.getTreeMaker().Variable(wc.getTreeMaker().
                                        Modifiers(EnumSet.noneOf(Modifier.class)), var.getVariable(), wc.getTreeMaker().Identifier(returnType), null)));
                                continue outer;
                            }
                        }
                    }
                    String baseName = name;
                    int cnt = 1;
                    while (SourceVersion.isKeyword(name) || localVarList.contains(name)) {
                        name = CodeStyleUtils.addPrefixSuffix(baseName + cnt++, "", "");
                    }
                    localVarList.add(name);
                    bindTree.add((BindingPatternTree) wc.getTreeMaker().BindingPattern(wc.getTreeMaker().Variable(wc.getTreeMaker().
                            Modifiers(EnumSet.noneOf(Modifier.class)), name, wc.getTreeMaker().Identifier(returnType), null)));
                }
                recordMap.put(p, bindTree);
            }

            DeconstructionPatternTree d = (DeconstructionPatternTree) createNestedPattern((PatternTree) t.getLeaf(), wc, recordMap);
            while (t != null && t.getLeaf().getKind() != Tree.Kind.IF) {  //TO BE MODIFIED
                t = t.getParentPath();
            }
            IfTree it = (IfTree) t.getLeaf();
            InstanceOfTree iot = (InstanceOfTree) ((ParenthesizedTree) it.getCondition()).getExpression();
            StatementTree bt = it.getThenStatement();
            InstanceOfTree cond = wc.getTreeMaker().InstanceOf(iot.getExpression(), d);
            for (Tree tree : replaceOccurrences) {
                bt = wc.getTreeMaker().removeBlockStatement((BlockTree) bt, (StatementTree) tree);
            }
            wc.rewrite(it, wc.getTreeMaker().If(wc.getTreeMaker().Parenthesized(cond), bt, it.getElseStatement()));
        }
    }

    private static PatternTree createNestedPattern(PatternTree pTree, WorkingCopy wc, Map<PatternTree, List<PatternTree>> map) {
        if (pTree instanceof BindingPatternTree) {
            if (map.containsKey(pTree) && map.get(pTree) != null) {
                BindingPatternTree p = (BindingPatternTree) pTree;
                VariableTree v = (VariableTree) p.getVariable();
                return (DeconstructionPatternTree) wc.getTreeMaker().RecordPattern((ExpressionTree) v.getType(), map.get(pTree), v);
            } else {
                return pTree;
            }
        }
        DeconstructionPatternTree bTree = (DeconstructionPatternTree) pTree;
        List<PatternTree> list = new ArrayList<>();
        for (PatternTree p : bTree.getNestedPatterns()) {
            PatternTree val = createNestedPattern(p, wc, map);
            list.add(val);
        }
        return (DeconstructionPatternTree) wc.getTreeMaker().RecordPattern(bTree.getDeconstructor(), list, bTree.getVariable());
    }
}
