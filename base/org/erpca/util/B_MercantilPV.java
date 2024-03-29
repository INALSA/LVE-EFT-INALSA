/******************************************************************************
 * Product: Adempiere ERP & CRM Smart Business Solution                       *
 * This program is free software; you can redistribute it and/or modify it    *
 * under the terms version 2 of the GNU General Public License as published   *
 * by the Free Software Foundation. This program is distributed in the hope   *
 * that it will be useful, but WITHOUT ANY WARRANTY; without even the implied *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.           *
 * See the GNU General Public License for more details.                       *
 * You should have received a copy of the GNU General Public License along    *
 * with this program; if not, write to the Free Software Foundation, Inc.,    *
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.                     *
 * For the text or an alternative of this public license, you may reach us    *
 * Copyright (C) 2003-2013 E.R.P. Consultores y Asociados, C.A.               *
 * All Rights Reserved.                                                       *
 * Contributor(s): Yamel Senih www.erpconsultoresyasociados.com               *
 *****************************************************************************/
package org.erpca.util;

import java.io.File;
import java.io.FileWriter;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.logging.Level;

import org.compiere.model.MBank;
import org.compiere.model.MBankAccount;
import org.compiere.model.MOrgInfo;
import org.compiere.model.MPayment;
import org.compiere.model.MPaymentBatch;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;
import org.compiere.util.TimeUtil;

/**
 * @author <a href="mailto:dixon.22martinez@gmail.com">Dixon Martinez</a>
 * @contributor <a href="mailto:yamelsenih@gmail.com">Yamel Senih</a> <br>
 *              Change Payment Selection Check to Payment generated by
 *              confirmation <br>
 *              Debugging errors in code Export class for Mercantil Bank
 */
public class B_MercantilPV implements PaymentVerificationExport {

	/** Logger */
	static private CLogger s_log = CLogger.getCLogger(B_MercantilPV.class);

	/** BPartner Info Index for Account */
	private static final int BPA_A_ACCOUNT = 0;
	/** BPartner Info Index for Value */
	private static final int BPA_A_IDENT_SSN = 1;
	/** BPartner Info Index for Name */
	private static final int BPA_A_NAME = 2;
	/** BPartner Info Index for Swift Code */
	private static final int BPA_SWIFTCODE = 3;
	/** BPartner Info Index for e-mail */
	private static final int BPA_A_EMAIL = 4;

	/**************************************************************************
	 * Export to File
	 * 
	 * @param payments
	 *            array of checks
	 * @param file
	 *            file to export checks
	 * @return number of lines
	 */
	public int exportToFile(MPayment[] payments, File file, StringBuffer err) {
		if (payments == null || payments.length == 0)
			return 0;
		// delete if exists
		try {
			if (file.exists())
				file.delete();
		} catch (Exception e) {
			s_log.log(Level.WARNING,
					"Could not delete - " + file.getAbsolutePath(), e);
		}

		//
		// MPaySelection m_PaySelection = (MPaySelection)
		// checks[0].getC_PaySelection();
		MPayment m_Payment = (MPayment) payments[0];
		// Yamel Senih 2013-05-15 01:49:06
		// Change C_Batch_ID for PayConfirm_ID
		// Get Confirm
		int m_C_PaymentBatch_ID = m_Payment.get_ValueAsInt("PayConfirm_ID");
		MPaymentBatch m_PaymentBatch = new MPaymentBatch(m_Payment.getCtx(),
				m_C_PaymentBatch_ID, m_Payment.get_TrxName());
		// End Yamel Senih
		MBankAccount m_BankAccount = (MBankAccount) m_Payment
				.getC_BankAccount();
		MOrgInfo orgInfo = MOrgInfo.get(m_Payment.getCtx(),
				m_Payment.getAD_Org_ID(), m_Payment.get_TrxName());

		// Payments Generated
		int payGenerated = payments.length;

		// Process Organization Tax ID
		String orgTaxID = orgInfo.getTaxID().replace("-", "").trim();
		orgTaxID = orgTaxID.substring(1, (orgTaxID.length() >= 10 ? 10
				: orgTaxID.length()));
		orgTaxID = String.format("%1$" + 15 + "s", orgTaxID).replace(" ", "0");

		// Bank
		MBank m_bank = (MBank) m_BankAccount.getC_Bank();
		int daysDueCheck = m_bank.get_ValueAsInt("DaysDueCheck");

		// Account No
		String bankAccountNo = m_BankAccount.getAccountNo().trim();
		bankAccountNo = bankAccountNo.substring(0,
				(bankAccountNo.length() >= 20 ? 20 : bankAccountNo.length()));
		bankAccountNo = bankAccountNo.replace(" ", "");
		bankAccountNo = String.format("%1$-" + 20 + "s", bankAccountNo)
				.replace(" ", "0");

		// Format Date
		String format = "ddMMyyyy";
		SimpleDateFormat sdf = new SimpleDateFormat(format);
		// Format Time
		String timeFormat = "hhmmss";
		SimpleDateFormat stf = new SimpleDateFormat(timeFormat);

		// Verification Type
		String iden_Type_Org = orgInfo.getTaxID().substring(0, 1);

		// Batch Document No
		String batchDocNo = m_PaymentBatch.getDocumentNo();
		batchDocNo = batchDocNo.substring(0, (batchDocNo.length() >= 15 ? 15
				: batchDocNo.length()));
		batchDocNo = String.format("%1$" + 15 + "s", batchDocNo).replace(" ",
				"0");

		// Get Total Amount
		BigDecimal m_TotalAmt = DB.getSQLValueBDEx(m_Payment.get_TrxName(),
				"SELECT SUM(" + "			CASE "
						+ "				WHEN p.DocStatus IN('CO', 'CL') THEN p.PayAmt "
						+ "				ELSE 0 " + "			END) PayAmt "
						+ "FROM C_Payment p " + "WHERE p.PayConfirm_ID = ?",
				m_C_PaymentBatch_ID);

		// Payment Amount
		String totalAmt = String.format("%.2f", m_TotalAmt.abs())
				.replace(".", "").replace(",", "");
		totalAmt = String.format("%1$" + 15 + "s", totalAmt).replace(" ", "0");

		// Reserved Area
		String arResr = "";
		// Current Time
		Timestamp curDate = new Timestamp(System.currentTimeMillis());

		int noLines = 0;
		StringBuffer line = null;
		try {

			FileWriter fw = new FileWriter(file);

			// write header
			line = new StringBuffer();
			// Header
			line.append("1").append(batchDocNo) // Customer File Number or Lot
												// Number
					.append(sdf.format(curDate)) // Process date
					.append(stf.format(curDate)) // Process Time
					.append(iden_Type_Org) // Type of Person
					.append(orgTaxID) // Customer Company Identification
					.append(String.format("%0" + 7 + "d", payGenerated)) // Payments
																			// Generated
					.append(totalAmt) // Amount of Checks
					.append(String.format("%1$" + 142 + "s", arResr)) // Reserved
																		// Area
					.append(Env.NL);
			fw.write(line.toString());
			noLines++;

			// write lines
			for (int i = 0; i < payments.length; i++) {
				m_Payment = payments[i];
				if (m_Payment == null)
					continue;
				// BPartner Info
				String bp[] = getBPartnerInfo(m_Payment.getC_BPartner_ID());

				// Status
				String status = m_Payment.getDocStatus();
				// Process Status and Payment Amount
				BigDecimal m_Amt = m_Payment.getPayAmt();
				int statusValid;
				if (status.equals("CO") || status.equals("CL")) {
					statusValid = 0;
				} else {
					statusValid = 1;
					m_Amt = Env.ZERO;
				}

				// Payment Amount
				String amt = String.format("%.2f", m_Amt.abs())
						.replace(".", "").replace(",", "");
				amt = String.format("%1$" + 15 + "s", amt).replace(" ", "0");

				String chekNo = m_Payment.getCheckNo();
				// Yamel Senih 2013-05-15 01:52:13
				// Instance Check No when this is null
				if (chekNo == null)
					chekNo = new String();
				// End Yamel Senih
				chekNo = chekNo.substring(0, (chekNo.length() >= 11 ? 11
						: chekNo.length()));
				chekNo = String.format("%1$" + 11 + "s", chekNo).replace(" ",
						"0");

				// Payment Date (Date)
				String docDate = sdf.format(m_Payment.getDateAcct());
				// Payment Date Due
				String docDateDue = sdf.format(TimeUtil.addDays(
						m_Payment.getDateAcct(), daysDueCheck));

				// Line
				line = new StringBuffer();
				line.append("2") // Constant
						.append(bankAccountNo) // Org Account No
						.append(chekNo) // Number of check
						.append(bp[BPA_A_NAME]) // Business Partner Name for
												// Account
						.append(amt) // Payment Amount
						.append(docDate) // Date of issue
						.append(docDateDue) // Expiration date
						.append(statusValid) // Exchange Management
						.append(String.format("%1$" + 26 + "s", arResr)) // Reserved
																			// Area
						.append(Env.NL);
				fw.write(line.toString());
				noLines++;
			} // write line
			fw.flush();
			fw.close();
			// Close

		} catch (Exception e) {
			err.append(e.toString());
			s_log.log(Level.SEVERE, "", e);
			return -1;
		}

		return noLines;
	} // exportToFile

	/**
	 * Get Business Partner Information
	 * 
	 * @author <a href="mailto:yamelsenih@gmail.com">Yamel Senih</a> 16/04/2013,
	 *         20:14:38
	 * @param C_BPartner_ID
	 * @return
	 * @return String[]
	 */
	private String[] getBPartnerInfo(int C_BPartner_ID) {
		String[] bp = new String[5];
		// Sql

		/**
		 * Carlos Parada 2013-09-19 Correct Query for BPartner Without Account
		 * Bank
		 */
		/**
		 * String sql =
		 * "SELECT MAX(bpa.AccountNo) AccountNo, bpa.A_Ident_SSN, bpa.A_Name, bpb.SwiftCode, bpa.A_Email "
		 * + "FROM C_BP_BankAccount bpa " +
		 * "INNER JOIN C_Bank bpb ON(bpb.C_Bank_ID = bpa.C_Bank_ID) " +
		 * "RIGHT JOIN C_BPartner bp ON(bp.C_BPartner_ID=bpa.C_BPartner_ID) " +
		 * "WHERE bp.C_BPartner_ID = ? " +
		 * "AND ((bpa.IsActive = 'Y' AND bpa.IsACH = 'Y' ) OR bpa.C_BPartner_ID Is Null)"
		 * +
		 * "GROUP BY bpa.C_BPartner_ID, bpa.A_Ident_SSN, bpa.A_Name, bpb.SwiftCode, bpa.A_Email"
		 * ;
		 **/

		String sql = "SELECT MAX(bpa.AccountNo) AccountNo, Coalesce(bpa.A_Ident_SSN,bp.TaxID) A_Ident_SSN , Coalesce(bpa.A_Name,bp.Name) A_Name, bpb.SwiftCode, bpa.A_Email \n"
				+ "FROM C_BP_BankAccount bpa  \n"
				+ "INNER JOIN C_Bank bpb ON(bpb.C_Bank_ID = bpa.C_Bank_ID) \n"
				+ "RIGHT JOIN C_BPartner bp ON(bp.C_BPartner_ID=bpa.C_BPartner_ID) \n"
				+ "WHERE bp.C_BPartner_ID =  ? \n"
				+ "AND ((bpa.IsActive = 'Y' AND bpa.IsACH = 'Y' ) OR bpa.C_BPartner_ID Is Null) \n"
				+ "GROUP BY bpa.C_BPartner_ID, bpa.A_Ident_SSN, bpa.A_Name, bpb.SwiftCode, bpa.A_Email,bp.TaxID,bp.Name";

		/**
		 * End Carlos Parada
		 * */

		s_log.fine("SQL=" + sql);

		try {
			PreparedStatement pstmt = DB.prepareStatement(sql, null);
			pstmt.setInt(1, C_BPartner_ID);
			ResultSet rs = pstmt.executeQuery();
			//
			if (rs.next()) {
				bp[BPA_A_ACCOUNT] = rs.getString(1);
				if (bp[BPA_A_ACCOUNT] == null)
					bp[BPA_A_ACCOUNT] = "NO CUENTA";
				bp[BPA_A_IDENT_SSN] = rs.getString(2);
				if (bp[BPA_A_IDENT_SSN] == null)
					bp[BPA_A_IDENT_SSN] = "NO RIF/CI";
				bp[BPA_A_NAME] = rs.getString(3);
				if (bp[BPA_A_NAME] == null)
					bp[BPA_A_NAME] = "NO NOMBRE";
				bp[BPA_SWIFTCODE] = rs.getString(4);
				if (bp[BPA_SWIFTCODE] == null)
					bp[BPA_SWIFTCODE] = "NO SWIFT";
				bp[BPA_A_EMAIL] = rs.getString(5);
				if (bp[BPA_A_EMAIL] == null)
					bp[BPA_A_EMAIL] = "";
			} else {
				bp[BPA_A_ACCOUNT] = "NO CUENTA";
				bp[BPA_A_IDENT_SSN] = "NO RIF/CI";
				bp[BPA_A_NAME] = "NO NOMBRE";
				bp[BPA_SWIFTCODE] = "NO SWIFT";
				bp[BPA_A_EMAIL] = "";
			}
			rs.close();
			pstmt.close();
		} catch (SQLException e) {
			s_log.log(Level.SEVERE, sql, e);
		}
		return processBPartnerInfo(bp);
	} // getBPartnerInfo

	/**
	 * Process Business Partner Information
	 * 
	 * @author <a href="mailto:yamelsenih@gmail.com">Yamel Senih</a> 16/04/2013,
	 *         20:14:49
	 * @param bp
	 * @return
	 * @return String[]
	 */
	private String[] processBPartnerInfo(String[] bp) {
		// Process Business Partner Account No
		String bpaAccount = bp[BPA_A_ACCOUNT];
		bpaAccount = bpaAccount.substring(0, bpaAccount.length() >= 20 ? 20
				: bpaAccount.length());
		bpaAccount = String.format("%1$" + 20 + "s", bpaAccount).replace(" ",
				"0");
		bp[BPA_A_ACCOUNT] = bpaAccount;
		// Process Tax ID
		String bpaTaxID = bp[BPA_A_IDENT_SSN];
		bpaTaxID = bpaTaxID.replace("-", "").trim();
		bpaTaxID = bpaTaxID.substring(0, bpaTaxID.length() >= 15 ? 15
				: bpaTaxID.length());
		bpaTaxID = String.format("%1$-" + 15 + "s", bpaTaxID);
		bp[BPA_A_IDENT_SSN] = bpaTaxID;
		// Process Account Name
		String bpaName = bp[BPA_A_NAME];
		bpaName = bpaName.substring(0,
				bpaName.length() >= 120 ? 120 : bpaName.length());
		bpaName = String.format("%1$-" + 120 + "s", bpaName);
		bp[BPA_A_NAME] = bpaName;
		// Process Swift Code
		String bpaSwiftCode = bp[BPA_SWIFTCODE];
		bpaSwiftCode = bpaSwiftCode.substring(0,
				bpaSwiftCode.length() >= 12 ? 12 : bpaSwiftCode.length());

		bp[BPA_SWIFTCODE] = bpaSwiftCode;
		// Process e-mail
		String bpaEmail = bp[BPA_A_EMAIL];
		bpaName = bpaEmail.substring(0,
				bpaEmail.length() >= 60 ? 60 : bpaEmail.length());
		bpaEmail = String.format("%1$-" + 50 + "s", bpaEmail);
		bp[BPA_A_EMAIL] = bpaEmail;

		return bp;
	} // processBPartnerInfo

}
