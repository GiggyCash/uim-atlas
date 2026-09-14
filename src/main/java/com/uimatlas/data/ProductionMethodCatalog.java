package com.uimatlas.data;

import com.uimatlas.recommendation.MethodDefinition;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Bundled catalog index; no filesystem discovery, runtime downloads or skill registry. */
public final class ProductionMethodCatalog
{
    private static final String INDEX = "uimatlas/methods/catalogs.txt";

    public List<MethodDefinition> load(ClassLoader resources, Set<Integer> canonicalItemIds) throws IOException
    {
        Set<String> paths = new HashSet<>();
        Set<String> ids = new HashSet<>();
        List<MethodDefinition> methods = new ArrayList<>();
        MethodDefinitionLoader loader = new MethodDefinitionLoader();
        try (BufferedReader index = reader(resources, INDEX))
        {
            String path;
            while ((path = index.readLine()) != null)
            {
                if (!path.matches("uimatlas/methods/[a-z][a-z0-9-]*\\.json") || !paths.add(path))
                {
                    throw new IllegalArgumentException("Invalid or duplicate production catalog path: " + path);
                }
                try (BufferedReader input = reader(resources, path))
                {
                    for (MethodDefinition method : loader.loadProduction(input, canonicalItemIds))
                    {
                        if (!ids.add(method.getId()))
                        {
                            throw new IllegalArgumentException("Duplicate production method ID: " + method.getId());
                        }
                        methods.add(method);
                    }
                }
            }
        }
        if (methods.isEmpty())
        {
            throw new IllegalArgumentException("Empty production catalog index");
        }
        methods.sort(Comparator.comparing(MethodDefinition::getId));
        return List.copyOf(methods);
    }

    private BufferedReader reader(ClassLoader resources, String path) throws IOException
    {
        InputStream input = resources.getResourceAsStream(path);
        if (input == null)
        {
            throw new IOException("Missing production catalog resource: " + path);
        }
        return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    }
}
