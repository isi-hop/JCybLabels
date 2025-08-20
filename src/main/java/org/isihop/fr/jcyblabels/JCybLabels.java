package org.isihop.fr.jcyblabels;

/*
 * Copyright (C) 2025 tondeur-h
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.*;
import java.util.Base64;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class JCybLabels 
{

    String printername="";
    int port=0;
    String IP="";
    String dbUrl = "";
    String dbUser = "";
    String dbPassword = "XXXX";
    String dbPasswordEnc="";

    String query = "SELECT uuid,labellang,nom,prenom,nom2,ddn,ndglims,ndcyberlab FROM public.labels where ";

    //logs
    private static final  Logger logger = Logger.getLogger(JCybLabels.class.getName());
    
      /****************************
      *  lecture du fichier des propriétées
      *****************************/
    private void lire_properties() 
    {
        //récuperer le dossier de travail de l'application
      String pathApplication=System.getProperty("user.dir");
      String programName=this.getClass().getSimpleName();
      //creer l'objet properties permettant de relire le fichier properties
      Properties p=new Properties();
      FileInputStream inProp;

        try {
            //ouvrir le fichier
            //le fichier doit se nommer JCybLabels.properties et se trouver
            //obligatoirement dans le dossier de l'application
            inProp=new FileInputStream(pathApplication+"/"+programName+".properties");
            //lire les variables du fichier
            p.load(inProp);
            //numero port d'écoute 1234 par défaut
            printername=p.getProperty("printername", "PRINTER ANONYMOUS");
            port=Integer.parseInt(p.getProperty("port", "9100"), 10);
            IP=p.getProperty("IP","172.18.45.100");
            dbUrl = p.getProperty("dbUrl","jdbc:postgresql://vm299:5432/cyblabels");
            dbUser =p.getProperty("dbUser","pg");
            dbPasswordEnc = p.getProperty("dbPassword", "ear");
            query = query + p.getProperty("query","SELECT * FROM public.cyblabels");
            inProp.close();
            
            //log des variables lues
            logger.log(Level.INFO, "Définition des variables du fichier properties.");
            logger.log(Level.INFO, () -> "printername : "+printername);
            logger.log(Level.INFO, () -> "IP : "+IP);
            logger.log(Level.INFO, () -> "port : "+port);
            logger.log(Level.INFO, () -> "dbUrl : "+dbUrl);
            logger.log(Level.INFO, () -> "dbUser : "+dbUser);
            logger.log(Level.INFO, () -> "dbPassword : "+dbPasswordEnc);
            logger.log(Level.INFO, () -> "query : "+query);
            //***********************

        } catch (FileNotFoundException ex) {
            System.out.println(ex.getMessage());
            logger.log(Level.SEVERE, ex.getMessage());
            System.exit(0);
        } catch (IOException ex) {
            System.out.println(ex.getMessage());
            logger.log(Level.SEVERE, ex.getMessage());
            System.exit(0);
        }
    }
    
    /***********************
     *  Main entry point
     * @param args 
     **********************/
    public static void main(String[] args) 
    {
        new JCybLabels();
    }

    /************************
     *  Constructor
     ************************/
    public JCybLabels() 
    {
        //a ffiche header
        header();
        
        //lecture des paramètres du fichier properties
        lire_properties();
        
        //comparer le password BDD
        if (compare_password(dbPassword, dbPasswordEnc)==false)
        {
            System.out.println("DataBase password ne correspond pas !");
            System.exit(0);
        }
        
        //mise en place des logs.
        try
                {
                    logger.setUseParentHandlers(false);
                    FileHandler fh = new FileHandler("JCybLabels%g.log", 0, 1, true);
                    fh.setLevel(Level.ALL);
                    fh.setFormatter(new SimpleFormatter());
                    logger.addHandler(fh);
                } catch (IOException ioe) {
                    Logger.getLogger(JCybLabels.class.getName()).log(Level.SEVERE, null, ioe);
                }
        
        //go work
        extract_labels();        
    }

    
    /**************************
     * Extraire et imprimer
     **************************/
    public final void extract_labels()
    {
        //org.postgresql.Driver 42.7
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) 
        {
            while (rs.next()) 
            {
                String base64Data = rs.getString("labellang");
                byte[] decodedData=Base64.getDecoder().decode(base64Data);
                String decodedString = new String(decodedData, StandardCharsets.UTF_8);

                sendToPrinter(decodedData, IP, port); // IP de l'imprimante
                
                System.out.println(decodedString);
                logger.log(Level.INFO, "{0} ({1}) {2} {3}", new Object[]{rs.getString("nom"), rs.getString("nom2"), rs.getString("prenom"), rs.getString("ddn")});
                logger.log(Level.INFO, "{0} # {1} # {2}", new Object[]{rs.getString("ndglims"), rs.getString("ndcyberlab"), rs.getInt("uuid")});
                logger.log(Level.INFO, "{0} IP={1}:{2} a traiter =>", new Object[]{printername, IP, port});
                logger.log(Level.INFO, decodedString);
                
                updateLigne(conn,rs.getInt("uuid"));  
            }

            System.out.println("Fin du job...");
            logger.log(Level.INFO, "Fin du Job...");
            
        } catch (Exception e) 
        {
            System.out.println(e.getMessage());
            logger.log(Level.SEVERE, e.getMessage());
        }
    }
    
    
    /*******************************
     * Imprimer
     * @param data
     * @param printerIp
     * @param port 
     *******************************/
    public void sendToPrinter(byte[] data, String printerIp, int port) 
    {
        try (Socket socket = new Socket(printerIp, port);
             OutputStream out = socket.getOutputStream()) 
        {
            out.write(data);
            out.flush();
            out.close();
            System.out.println("Donnees envoyees à l'imprimante "+printerIp+":"+port);
            System.out.println(bytesToHex(data));
            logger.log(Level.INFO, "Donnees envoyees \u00e0 l''imprimante {0}:{1}", new Object[]{printerIp, port});
            logger.log(Level.INFO, bytesToHex(data));
        } catch (Exception e) 
        {
            System.err.println("Erreur lors de l'envoi à l'imprimante : " + e.getMessage());
            logger.log(Level.SEVERE, e.getMessage());
        }
    }

    
    /**********************************
     * Mettre a jour le champs...
     * @param conn
     * @param aInt 
     **********************************/
    private void updateLigne(Connection conn, int aInt) 
    {
        try 
        {
            Statement stmtUP = conn.createStatement();
            String sql="UPDATE public.labels set imprime='1' where uuid='"+aInt+"'";
            logger.log(Level.INFO, "{0} uuid set to 1 for imprime.", aInt);
            stmtUP.executeUpdate(sql);
        } catch (SQLException ex) 
        {
            System.out.println(ex.getMessage());
            logger.log(Level.SEVERE, ex.getMessage());
        }   
    }
    
    
    /*******************************
     * Byte[]toString
     * @param bytes
     * @return 
     *******************************/
    public static String bytesToHex(byte[] bytes) 
    {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) 
        {
            hexString.append(String.format("%02X", b)); // "%02X" pour deux chiffres hex en majuscules
        }
        return hexString.toString();
    }
    
    
    /*******************************
     * RunTime Header
     *******************************/
    public final void header()
    {
        String header="""
                      JCybLabels version 0.1
                      Copyright (C) 2025 tondeur-h
                      
                      This program is free software: you can redistribute it and/or modify
                      it under the terms of the GNU General Public License as published by
                      the Free Software Foundation, either version 3 of the License, or
                      (at your option) any later version.
                      
                      This program is distributed in the hope that it will be useful,
                      but WITHOUT ANY WARRANTY; without even the implied warranty of
                      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
                      GNU General Public License for more details.
                      
                      You should have received a copy of the GNU General Public License
                      along with this program.  If not, see <http://www.gnu.org/licenses/>.
                      
                      Lire le fichier logs JCybLabels.logs pour toutes informations.
                      Ne pas oublier de mettre en place le fichier JCybLabels dans le dossier local de l'application.
                      """;
        System.out.println(header);
    }
    
    
/***********************************
 * Encrypter une chaine 
 * @param input
 * @return 
 ************************************/    
public String encryptSHA512(String input) 
{
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b)); // Convertit en hexadécimal
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-512 algorithm not found");
        }
    }

    /****************************
     * Verifier le password
     * @param locdbPassword
     * @param locdbPasswordEnc
     * @return 
     *****************************/
    private boolean compare_password(String locdbPassword, String locdbPasswordEnc) {
        boolean retour=false;
        
        retour = locdbPasswordEnc.compareTo(encryptSHA512(locdbPassword))==0;
        
        return retour;
    }

}