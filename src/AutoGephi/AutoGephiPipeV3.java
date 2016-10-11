package AutoGephi;
import java.util.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gephi.data.attributes.api.AttributeColumn;
import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.dynamic.api.DynamicController;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.UndirectedGraph;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.EdgeDefault;
import org.gephi.io.importer.api.ImportController;
import org.gephi.io.processor.plugin.DefaultProcessor;
import org.gephi.io.processor.plugin.DynamicProcessor;
import org.gephi.layout.plugin.circularlayout.radialaxislayout.RadialAxisLayout;
import org.gephi.partition.api.Partition;
import org.gephi.partition.api.PartitionController;
import org.gephi.partition.plugin.NodeColorTransformer;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.ranking.api.Ranking;
import org.gephi.ranking.api.RankingController;
import org.gephi.ranking.api.Transformer;
import org.gephi.ranking.plugin.transformer.AbstractSizeTransformer;
import org.gephi.statistics.plugin.GraphDistance;
import org.gephi.statistics.plugin.Modularity;
import org.openide.util.Lookup;
import org.openide.util.Utilities;

/**
 *
 * @author motion411
 */
public class AutoGephiPipeV3 
{
    private static Workspace workspace;
    private static GraphModel graphModel; 
    private static AttributeModel attributeModel;
    private static ImportController importController;
    private static Graph graph;
    private static String processedFile;
    private static DynamicProcessor dynamicProcessor;
    private static Container container;
    public static Scanner read;
    public static String year, month,day;
    
    //fileDate is a regex for extracting the date of each imported file and uses it to append as TimeInterval
    private static String fileDate="^[a-zA-Z0-9/*]+((([0-1][0-9]{3})|([2][0][0-9]{2}))[-](([0][1-9])|([1][0-2]))[-](([0][1-9])|([1-2][0-9])|([3][0-1]))+).*";
    public static String interiorDate="-?\\d+";
    

    
    
    public static void initialize()//Initialize a project and a workspace
    {
        ProjectController pc = Lookup.getDefault().lookup(ProjectController.class);
        pc.newProject();
        workspace = pc.getCurrentWorkspace();
        importController = Lookup.getDefault().lookup(ImportController.class);
        graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
        attributeModel = Lookup.getDefault().lookup(AttributeController.class).getModel();
         //Initialize the DynamicProcessor - which will append the container to the workspace
        dynamicProcessor = new DynamicProcessor();
        dynamicProcessor.setDateMode(true);    //Set 'true' if you set real dates (ex: yyyy-mm-dd), it's double otherwise
        dynamicProcessor.setLabelmatching(true);   //Set 'true' if node matching is done on labels instead of ids
        
        //graph = graphModel.getUndirectedGraph();
        graph = graphModel.getGraph();
    }
    
    public static void radialAxLayout()
    {
        RadialAxisLayout radLayout = new RadialAxisLayout(null,1.0,false);
        radLayout.setGraphModel(graphModel);
        radLayout.resetPropertiesValues();
        radLayout.setScalingWidth(1.0);      
        //Node placement
        ///Makes each Spar a seperate Modularity Class
        radLayout.setNodePlacement(Modularity.MODULARITY_CLASS+"-Att");
        ///Nodes are positioned in spar by their betweenness centrality
        radLayout.setSparNodePlacement(GraphDistance.BETWEENNESS+"-Att");  
        radLayout.setSparSpiral(true);
        radLayout.setSparOrderingDirection(Boolean.FALSE);
        radLayout.setKnockdownSpars(Boolean.FALSE);
        //Debugging prompts
        //System.out.println(radLayout.getSparNodePlacement().toString());
        //System.out.println(radLayout.getNodePlacement().toString());
        //System.out.println(radLayout.getNodePlacementDirection().toString());
       
        
        radLayout.initAlgo();//start algorithm
        for(int i=0; i<100 && radLayout.canAlgo(); i++)
        {
            radLayout.goAlgo();
        }
    }
    
    public static void sizeNodes()
    {
        //Get Centrality and then size nodes by measure
        GraphDistance distance = new GraphDistance();
        distance.setDirected(true);
        distance.execute(graphModel, attributeModel);
        //Size by Betweeness centrality
        RankingController rankingController = Lookup.getDefault().lookup(RankingController.class);
        AttributeColumn centralityColumn = attributeModel.getNodeTable().getColumn(GraphDistance.BETWEENNESS);
        Ranking centralityRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, centralityColumn.getId());
        AbstractSizeTransformer sizeTransformer = (AbstractSizeTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_SIZE);
        sizeTransformer.setMinSize(20);
        sizeTransformer.setMaxSize(100);
        rankingController.transform(centralityRanking,sizeTransformer);
    }
    
    public static void colorByCommunity()
    {
        ///Color by Community but running modularity measures
        PartitionController partitionController = Lookup.getDefault().lookup(PartitionController.class);
        
         //Run modularity algorithm - community detection
        Modularity modularity = new Modularity();
        modularity.setResolution(0.15);
        modularity.execute(graphModel, attributeModel);

        //Partition with 'modularity_class', just created by Modularity algorithm
        AttributeColumn modColumn = attributeModel.getNodeTable().getColumn(Modularity.MODULARITY_CLASS);
        //AttributeColumn timeColumn=attributeModel.getNodeTable().
        Partition p2 = partitionController.buildPartition(modColumn, graph);
        System.out.println(p2.getPartsCount() + " Communities found");
        NodeColorTransformer nodeColorTransformer2 = new NodeColorTransformer();
        nodeColorTransformer2.randomizeColors(p2);
        partitionController.transform(p2, nodeColorTransformer2);
    }
    /*
     public static void importGraph(String fileName)//////imports graph and appends it to existing graph
    {
       // ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        //Container container;
        
        Pattern p=Pattern.compile(fileDate);//used to extract FileDate from imported graph
        try//place imported data into container
        {
            //NEED TO UPDATE TO IMPORT VIA COMMAND LINE
            //File file=Utilities.toFile(AutoGephiPipeV3.class.getResource(fileName).toURI());
           // File file=Utilities.toFile(fileName.toURI());
            File file;
            try
            {
                //Takes in files from parent directory
                file=new File("../"+fileName);
            }
            catch(Exception ex)
            {
                ex.printStackTrace();
                return;
            }
            
            System.out.println("File imported: " +file.toString());
            container = importController.importFile(file);
            container.getLoader().setEdgeDefault(EdgeDefault.UNDIRECTED);//set to undirected
            System.out.println(fileName+" was appended to graph.");
            
            String[] tokens = fileName.split("\\.(?=[^\\.]+$)");///Eliminate input extension for use in output name.
            processedFile=tokens[0];///Used for export file name
            //Searches file name for a proper date and then appends the date as a time interval
            Matcher m = p.matcher(fileName);
            if (m.find()) 
            {
                //Set date for this file
                System.out.println(fileName);
                System.out.println(m.group(1));
                dynamicProcessor.setDate(m.group(1));///Set time interval
                System.out.println("Date Set: " +dynamicProcessor.getDate());
            }
            else
            {
                System.out.println("invalid date");
            }   
              
            
            //Process the container using the DynamicProcessor
            importController.process(container, dynamicProcessor, workspace);
            
            
        }
        catch(Exception ex)
        {
           // System.out.println(fileName+" was NOT appended to graph.");
            ex.printStackTrace();
            return;
        }
        //Append imported data to GraphAPI
        importController.process(container, new DefaultProcessor(), workspace);
        
    }*/
    
//    public static void importGraph(File file)//////imports graph and appends it to existing graph
//    {
//       // ImportController importController = Lookup.getDefault().lookup(ImportController.class);
//        //Container container;
//        
//        Pattern p=Pattern.compile(fileDate);//used to extract FileDate from imported graph
//        try//place imported data into container
//        {
//            //NEED TO UPDATE TO IMPORT VIA COMMAND LINE
//            //File file=Utilities.toFile(AutoGephiPipeV3.class.getResource(fileName).toURI());
//           // File file=Utilities.toFile(fileName.toURI());
//            String fileName=file.getName();
//            
//            System.out.println("File imported: " +file.toString());
//            container = importController.importFile(file);
//            container.getLoader().setEdgeDefault(EdgeDefault.UNDIRECTED);//set to undirected
//            System.out.println(fileName+" was appended to graph.");
//            
//            String[] tokens = fileName.split("\\.(?=[^\\.]+$)");///Eliminate input extension for use in output name.
//            processedFile=tokens[0];///Used for export file name
//            //Searches file name for a proper date and then appends the date as a time interval
//            Matcher m = p.matcher(fileName);
//            if (m.find()) 
//            {
//                //Set date for this file
//                System.out.println(fileName);
//                System.out.println(m.group(1));
//                dynamicProcessor.setDate(m.group(1));///Set time interval
//                System.out.println("Date Set: " +dynamicProcessor.getDate());
//            }
//            else
//            {
//                System.out.println("invalid date");
//            }   
//              
//            
//            //Process the container using the DynamicProcessor
//            importController.process(container, dynamicProcessor, workspace);
//            
//            
//        }
//        catch(Exception ex)
//        {
//           // System.out.println(fileName+" was NOT appended to graph.");
//            ex.printStackTrace();
//            return;
//        }
//        //Append imported data to GraphAPI
//        //importController.process(container, new DefaultProcessor(), workspace);
//        
//    }
    public static void importGraph(File file)//////imports graph and appends it to existing graph
    {
       // ImportController importController = Lookup.getDefault().lookup(ImportController.class);
        //Container container;
        
        Pattern p=Pattern.compile(fileDate);//used to extract FileDate from imported graph
        try//place imported data into container
        {
            //NEED TO UPDATE TO IMPORT VIA COMMAND LINE
            //File file=Utilities.toFile(AutoGephiPipeV3.class.getResource(fileName).toURI());
           // File file=Utilities.toFile(fileName.toURI());
            String fileName=file.getName();
            
            System.out.println("File imported: " +file.toString());
            container = importController.importFile(file);
            container.getLoader().setEdgeDefault(EdgeDefault.UNDIRECTED);//set to undirected
            System.out.println(fileName+" was appended to graph.");
            
            String[] tokens = fileName.split("\\.(?=[^\\.]+$)");///Eliminate input extension for use in output name.
            processedFile=tokens[0];///Used for export file name
            //Searches file name for a proper date and then appends the date as a time interval
            Matcher m = p.matcher(fileName);
            if (m.find()) 
            {
                //Set date for this file
                System.out.println(fileName);
                System.out.println(m.group(1));
                dynamicProcessor.setDate(m.group(1));///Set time interval
                System.out.println("Date Set: " +dynamicProcessor.getDate());
            }
            else
            {
                System.out.println("invalid date");
                ///////Attempt to grab date from file itself
                int count=0;
                
                try
		{
			//file=new File(file);
			read=new Scanner(file);
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			return;
		}
                while(read.hasNext())
                {
                    //System.out.println(read.next());
                    String temp=read.next();
                    p=Pattern.compile(interiorDate);
                    m=p.matcher(temp);
                    if(m.find())
                    {
                        //System.out.println(temp);
                        count++;
                        if(count==1 && temp.length()==4)
                        {
                            year=temp;
                        }
                        else if(count==2)
                        {
                            if(temp.length()==1)
                            {
                                temp="0"+temp;
                                month=temp;
                            }
                            else
                            {
                                month=temp;
                            }
                            
                        }
                        else if(count==3)
                        {
                            if(temp.length()==1)
                            {
                                temp="0"+temp;
                                day=temp;
                            }
                            else
                            {
                                day=temp;
                            }
                            break;
                        }
                       
                    }
                    
                }
                System.out.println(year+"-"+month+"-"+day);
                String newName=year+"-"+month+"-"+day;
                dynamicProcessor.setDate(newName);///Set time interval
                System.out.println("Date Set: " +dynamicProcessor.getDate());
                
                
                
            }   
              
            
            //Process the container using the DynamicProcessor
            importController.process(container, dynamicProcessor, workspace);
            
            
        }
        catch(Exception ex)
        {
           // System.out.println(fileName+" was NOT appended to graph.");
            ex.printStackTrace();
            return;
        }
        //Append imported data to GraphAPI
        //importController.process(container, new DefaultProcessor(), workspace);
        
    }
    
    public static void importDirectory(String dirName)
    {
        //File[] files = new File("../"+dirName).listFiles();
        File tempFile=new File(dirName);
        if(tempFile.isDirectory()==true)
        {
            File[] files = new File(dirName).listFiles();
            System.out.println(dirName);
            //System.out.println(files.length);

            for (int i = 0; i <= files.length - 1; i++) {
                try {
                    //importGraph(files[i].getName());
                    importGraph(files[i]);
                } catch (Exception e) {
                    System.out.println(files[i].toString() + " is not a graph file");
                }
            }
        }
        else if(tempFile.isFile()==true)
        {
            try
            {
                importGraph(tempFile);
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    } 
    
    
    public static void exportGraph()
    {
        
        //Set 'show labels' option in Preview - and disable node size influence on text size
        PreviewModel previewModel = Lookup.getDefault().lookup(PreviewController.class).getModel();
        previewModel.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
        previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_PROPORTIONAL_SIZE, Boolean.FALSE);

        //Export a pdf for visual debugging purposes, gexf for gephi
        ExportController ec = Lookup.getDefault().lookup(ExportController.class);
        try 
        {
            //ec.exportFile(new File("ranking.gexf"));
            ec.exportFile(new File(processedFile+".gexf"));
            ec.exportFile(new File(processedFile+".pdf"));
           // ec.exportFile(new File("ranking.pdf"));
           // ec.exportFile(new File("ranking.gml"));    
        } 
        catch (IOException ex) 
        {
            ex.printStackTrace();
            return;
        }
    }
    
    
}